import os
import subprocess
from pathlib import Path
from typing import Generator, Optional
import time

import pytest

# Optional imports for integration environment. If not available, tests will be skipped
# or will fallback to a locally provided PostgreSQL (via env vars).
try:
    import psycopg2  # type: ignore
    _HAVE_PSYCOPG2 = True
except Exception as _psycopg_err:  # pragma: no cover - environment dependent
    psycopg2 = None  # type: ignore
    _HAVE_PSYCOPG2 = False
    _PSYCOPG_ERR = _psycopg_err

try:
    from testcontainers.postgres import PostgresContainer  # type: ignore
    _HAVE_TESTCONTAINERS = True
    _TESTCONTAINERS_ERR = None
except Exception as _tc_err:  # pragma: no cover - environment dependent
    PostgresContainer = object  # type: ignore
    _HAVE_TESTCONTAINERS = False
    _TESTCONTAINERS_ERR = _tc_err

# Hardcoded Docker configuration for test container
POSTGRES_IMAGE = "postgres:16-alpine"
POSTGRES_USER = "test_user"
POSTGRES_PASSWORD = "test_password"
POSTGRES_DB = "test_db"
CONTAINER_STARTUP_TIMEOUT = 120  # 2 minutes for Windows Docker


def _is_docker_running() -> tuple[bool, str]:
    """
    Vérifie si Docker Desktop est démarré et accessible.
    
    Returns:
        tuple[bool, str]: (is_running, message)
    """
    try:
        # Exécuter 'docker info' pour vérifier l'état du daemon
        result = subprocess.run(
            ["docker", "info"],
            capture_output=True,
            text=True,
            timeout=10
        )
        if result.returncode == 0:
            return True, "Docker daemon is running"
        else:
            return False, f"Docker daemon not responding: {result.stderr.strip()}"
    except subprocess.TimeoutExpired:
        return False, "Docker command timed out after 10 seconds"
    except FileNotFoundError:
        return False, "Docker CLI not found - is Docker Desktop installed?"
    except Exception as e:
        return False, f"Failed to check Docker status: {str(e)}"


def _create_schema_and_load_data(conn) -> None:
    """
    Create minimal schema required by data.sql and load its content.
    This duplicates only what is necessary for tests and keeps business meaning:
    - Global config table (singleton id=1)
    - PII type config table with unique key (pii_type, detector)
    """
    with conn.cursor() as cur:
        # Create tables if they don't exist
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS pii_detection_config (
                id              INTEGER PRIMARY KEY,
                gliner_enabled  BOOLEAN NOT NULL,
                presidio_enabled BOOLEAN NOT NULL,
                regex_enabled   BOOLEAN NOT NULL,
                default_threshold NUMERIC(4,2) NOT NULL,
                updated_at      TIMESTAMP NULL,
                updated_by      VARCHAR(128) NULL
            );
            """
        )

        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS pii_type_config (
                pii_type       VARCHAR(64) NOT NULL,
                detector       VARCHAR(32) NOT NULL,
                enabled        BOOLEAN NOT NULL,
                threshold      NUMERIC(4,2) NOT NULL,
                display_name   VARCHAR(256) NOT NULL,
                description    TEXT NULL,
                category       VARCHAR(128) NULL,
                country_code   VARCHAR(8) NULL,
                detector_label VARCHAR(128) NULL,
                created_at     TIMESTAMP NULL,
                updated_at     TIMESTAMP NULL,
                updated_by     VARCHAR(128) NULL,
                CONSTRAINT uq_pii_type_detector UNIQUE (pii_type, detector)
            );
            """
        )

        conn.commit()

        # Load data.sql from the Java API module resources
        # __file__ -> .../ai-sentinel/pii-detector-service/tests/integration/fixtures/database_fixtures.py
        # parents[4] points to the repository root: .../ai-sentinel
        root = Path(__file__).resolve().parents[4]
        data_sql_path = root / "pii-reporting-api" / "src" / "main" / "resources" / "data.sql"
        with data_sql_path.open("r", encoding="utf-8") as f:
            sql_script = f.read()

        # Clean SQL script: remove comments and empty lines
        cleaned_lines = []
        for line in sql_script.split("\n"):
            line = line.strip()
            # Skip comment-only lines and empty lines
            if line and not line.startswith("--"):
                cleaned_lines.append(line)
        
        cleaned_sql = " ".join(cleaned_lines)
        
        # Split by semicolon to get individual statements
        statements = [stmt.strip() for stmt in cleaned_sql.split(";") if stmt.strip()]
        
        # Execute each statement
        executed_count = 0
        for stmt in statements:
            try:
                cur.execute(stmt)
                executed_count += 1
            except Exception as e:
                # Log failed statement for debugging
                print(f"WARNING: Failed to execute statement: {str(e)}")
                print(f"Statement was: {stmt[:100]}...")
                # Don't raise - continue with other statements
        
        conn.commit()
        print(f"INFO: Successfully executed {executed_count}/{len(statements)} SQL statements from data.sql")


def _get_env_db_params() -> Optional[dict]:
    """Return DB connection params from environment if fully provided."""
    host = os.getenv("DB_HOST")
    port = os.getenv("DB_PORT")
    name = os.getenv("DB_NAME")
    user = os.getenv("DB_USER")
    pwd = os.getenv("DB_PASSWORD")
    if all([host, port, name, user, pwd]):
        return {
            "host": host,
            "port": int(port),
            "dbname": name,
            "user": user,
            "password": pwd,
        }
    return None


@pytest.fixture(scope="module")
def postgres_container() -> Generator[PostgresContainer, None, None]:
    """Provide a PostgreSQL for integration tests.

    Preference order:
    1) Testcontainers (Docker) if available
    2) Locally provided PostgreSQL via DB_* environment variables
    3) Skip with an explicit reason
    """
    if not _HAVE_PSYCOPG2:
        pytest.skip(f"Skipping integration tests - psycopg2 missing: {_PSYCOPG_ERR}")

    # Branch 1: Testcontainers available
    if _HAVE_TESTCONTAINERS:
        # Pre-check: Verify Docker daemon is accessible
        docker_running, docker_msg = _is_docker_running()
        if not docker_running:
            env_params = _get_env_db_params()
            if not env_params:
                pytest.skip(
                    f"Skipping integration tests - Docker Desktop n'est pas démarré.\n"
                    f"Raison: {docker_msg}\n"
                    f"Solution: Démarrez Docker Desktop et attendez qu'il soit complètement prêt (icône verte dans la barre des tâches), "
                    f"puis relancez les tests."
                )
            # Continue to env branch if DB_* vars are provided
        
        try:
            # Initialize PostgresContainer with hardcoded test parameters
            pg = PostgresContainer(
                image=POSTGRES_IMAGE,
                username=POSTGRES_USER,
                password=POSTGRES_PASSWORD,
                dbname=POSTGRES_DB
            )
            
            with pg:
                # Wait for container to be fully ready
                time.sleep(5)
                
                # Establish a direct psycopg2 connection to create schema and load data
                conn = psycopg2.connect(
                    host=pg.get_container_host_ip(),
                    port=int(pg.get_exposed_port("5432")),
                    dbname=POSTGRES_DB,
                    user=POSTGRES_USER,
                    password=POSTGRES_PASSWORD,
                )
                try:
                    _create_schema_and_load_data(conn)
                finally:
                    conn.close()

                # Configure env vars for the adapter under test
                os.environ["DB_HOST"] = pg.get_container_host_ip()
                os.environ["DB_PORT"] = str(pg.get_exposed_port("5432"))
                os.environ["DB_NAME"] = POSTGRES_DB
                os.environ["DB_USER"] = POSTGRES_USER
                os.environ["DB_PASSWORD"] = POSTGRES_PASSWORD

                yield pg
                return
        except Exception as e:
            # Fall back to env-based if provided; else skip
            env_params = _get_env_db_params()
            if not env_params:
                pytest.skip(
                    f"Skipping integration tests - cannot start Postgres container: {e}"
                )
            # Continue to env branch below

    # Branch 2: Use locally provided Postgres (via env)
    env_params = _get_env_db_params()
    if env_params:
        try:
            conn = psycopg2.connect(**env_params)
            try:
                _create_schema_and_load_data(conn)
            finally:
                conn.close()
            # Ensure env is set (already is) for adapter
            yield None  # type: ignore
            return
        except Exception as e:
            pytest.skip(
                f"Skipping integration tests - failed to use local Postgres from env: {e}"
            )

    # Branch 3: Skip explicitly with clear reason
    reason = (
        f"Skipping integration tests - dependencies missing: "
        f"testcontainers({_TESTCONTAINERS_ERR}) and no DB_* environment provided"
    )
    pytest.skip(reason)
