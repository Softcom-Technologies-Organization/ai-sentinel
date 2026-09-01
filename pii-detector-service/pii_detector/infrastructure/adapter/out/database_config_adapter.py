"""
Database adapter for fetching PII detection configuration from PostgreSQL.

This module provides a simple, reliable way to fetch the dynamic configuration
from the shared PostgreSQL database when requested via the gRPC fetch_config_from_db flag.
"""

import logging
import os
from typing import Callable, Any, Optional

import psycopg2
from psycopg2.extras import RealDictCursor

logger = logging.getLogger(__name__)

# Upper bound of the on-demand benchmark when the row carries none (column default).
DEFAULT_BENCH_MAX_CONCURRENCY = 4


class DatabaseConfigAdapter:
    """Adapter for fetching PII detection configuration from PostgreSQL."""

    def __init__(self):
        """Initialize database connection parameters from environment variables."""
        self.host = os.getenv("DB_HOST", "postgres")
        self.port = int(os.getenv("DB_PORT", "5432"))
        self.database = os.getenv("DB_NAME", "ai-sentinel")
        self.user = os.getenv("DB_USER")
        self.password = os.getenv("DB_PASSWORD")
        if not self.user or not self.password:
           logger.warning(
               "DB_USER or DB_PASSWORD not set. Database connections will fail."
           )

    def _get_connection(self):
        """Create and return a database connection."""
        return psycopg2.connect(
            host=self.host,
            port=self.port,
            database=self.database,
            user=self.user,
            password=self.password,
            connect_timeout=5,
        )

    def fetch_config(self) -> Optional[dict]:
        """
        Fetch PII detection configuration from database.

        Returns:
            Dictionary with config keys: presidio_enabled, regex_enabled,
            ministral_enabled, ministral_chunk_size, ministral_overlap,
            default_threshold, postfilter_enabled.
            Returns None if fetch fails.

        Business Rule: Single-row configuration table with id=1.

        Note: the Ministral and ``postfilter_enabled`` columns are read
        defensively via a UndefinedColumn fallback so the adapter stays
        compatible with deployments that have not yet applied the migration
        adding these columns.
        """
        connection = None
        cursor = None

        try:
            connection = self._get_connection()
            cursor = connection.cursor(cursor_factory=RealDictCursor)

            # Query the single-row configuration table. The Ministral and
            # postfilter columns are read defensively: a UndefinedColumn
            # fallback keeps existing rows readable even if the columns have not
            # been added yet by Hibernate DDL update on a freshly-pulled
            # environment.
            query = """
                    SELECT presidio_enabled,
                        regex_enabled,
                        ministral_enabled,
                        ministral_chunk_size,
                        ministral_overlap,
                        default_threshold,
                        postfilter_enabled,
                        lm_studio_host,
                        lm_studio_port,
                        ministral_concurrency,
                        ministral_concurrency_auto,
                        ministral_concurrency_tuned_signature
                    FROM pii_detection_config
                    WHERE id = 1 \
                    """

            try:
                cursor.execute(query)
                result = cursor.fetchone()
            except psycopg2.errors.UndefinedColumn:
                # Migration not yet applied for ministral_* / postfilter_enabled /
                # lm_studio_* / ministral_concurrency_* -- retry without the new
                # columns. They all default downstream so the service stays usable
                # on a freshly-pulled environment before Hibernate DDL update.
                logger.warning(
                    "ministral_* / postfilter_enabled / lm_studio_* / "
                    "ministral_concurrency_* column missing in pii_detection_config; "
                    "falling back on defaults. Apply migration to enable."
                )
                connection.rollback()
                cursor.execute(
                    """
                    SELECT
                        presidio_enabled,
                        regex_enabled,
                        FALSE AS ministral_enabled,
                        2048 AS ministral_chunk_size,
                        410 AS ministral_overlap,
                        default_threshold,
                        'localhost' AS lm_studio_host,
                        1234 AS lm_studio_port,
                        1 AS ministral_concurrency,
                        TRUE AS ministral_concurrency_auto,
                        NULL AS ministral_concurrency_tuned_signature
                    FROM pii_detection_config
                    WHERE id = 1
                    """
                )
                result = cursor.fetchone()

            if result is None:
                logger.warning(
                    "No configuration found in database (id=1). "
                    "Will use default configuration from TOML file."
                )
                return None

            config = dict(result)
            # Normalise the flags so downstream code can assume they are always
            # present (defaults applied when the migration is pending).
            config.setdefault("postfilter_enabled", False)
            if config["postfilter_enabled"] is None:
                config["postfilter_enabled"] = False
            # Ministral-PII detector flag + chunking knobs (added by migration
            # 013). Absent / NULL -> the documented defaults so a pre-migration
            # DB stays usable and the detector stays a no-op until opted in.
            config.setdefault("ministral_enabled", False)
            if config["ministral_enabled"] is None:
                config["ministral_enabled"] = False
            config.setdefault("ministral_chunk_size", 2048)
            if config["ministral_chunk_size"] is None:
                config["ministral_chunk_size"] = 2048
            config.setdefault("ministral_overlap", 410)
            if config["ministral_overlap"] is None:
                config["ministral_overlap"] = 410
            # LM Studio endpoint (host/port) serving the Ministral-PII model
            # (added by migration 014). Absent / NULL -> the documented defaults
            # (localhost:1234) so a pre-migration DB keeps working.
            config.setdefault("lm_studio_host", "localhost")
            if not config["lm_studio_host"]:
                config["lm_studio_host"] = "localhost"
            config.setdefault("lm_studio_port", 1234)
            if config["lm_studio_port"] is None:
                config["lm_studio_port"] = 1234
            # Ministral concurrency auto-tuning knobs (added by migration 015).
            # Absent / NULL -> sequential (1), auto-tune enabled, never tuned yet,
            # so a pre-migration DB keeps the historical single-prompt behaviour.
            config.setdefault("ministral_concurrency", 1)
            if config["ministral_concurrency"] is None:
                config["ministral_concurrency"] = 1
            config.setdefault("ministral_concurrency_auto", True)
            if config["ministral_concurrency_auto"] is None:
                config["ministral_concurrency_auto"] = True
            config.setdefault("ministral_concurrency_tuned_signature", None)
            logger.info(
                "Successfully fetched config from database: "
                f"presidio={config['presidio_enabled']}, "
                f"regex={config['regex_enabled']}, "
                f"ministral={config['ministral_enabled']}, "
                f"threshold={config['default_threshold']}, "
                f"postfilter={config['postfilter_enabled']}, "
                f"lm_studio={config['lm_studio_host']}:{config['lm_studio_port']}, "
                f"ministral_concurrency={config['ministral_concurrency']} "
                f"(auto={config['ministral_concurrency_auto']})"
            )
            return config

        except psycopg2.OperationalError as e:
            logger.exception(
                f"Database connection failed: {e}. "
                "Check DB_HOST, DB_PORT, DB_USER, DB_PASSWORD environment variables. "
                "Will use default configuration from TOML file."
            )
            return None

        except psycopg2.Error as e:
            logger.exception(
                f"Database query failed: {e}. "
                "Will use default configuration from TOML file."
            )
            return None

        except Exception as e:
            logger.exception(
                f"Unexpected error fetching config: {e}. "
                "Will use default configuration from TOML file."
            )
            return None

        finally:
            if cursor:
                cursor.close()
            if connection:
                connection.close()

    def fetch_pii_type_configs(self, detector: str = None) -> Optional[dict]:
        """
        Fetch PII type-specific configurations from database.

        Args:
            detector: Optional detector filter ('PRESIDIO', 'REGEX', 'MINISTRAL').
                     If None, fetches all PII type configs.

        Returns:
            Dictionary mapping PII type to config dict with keys:
            - enabled (bool): Whether this PII type is enabled
            - threshold (float): Detection threshold for this type (0.0-1.0)
            - detector (str): Detector name (PRESIDIO, REGEX, MINISTRAL)
            - category (str): PII category
            - country_code (str): Optional country code
            - detector_label (str): Detector-specific label

            Example: {
                'EMAIL': {'enabled': True, 'threshold': 0.5, 'detector': 'PRESIDIO', ...},
                'CREDIT_CARD': {'enabled': False, 'threshold': 0.7, 'detector': 'REGEX', ...}
            }

            Returns None if fetch fails.

        Business Rule: PII type configs control which types are detected and
        their confidence thresholds on a per-type basis.
        """
        self.logger = logging.getLogger(f"{__name__}.{self.__class__.__name__}")

        connection = None
        cursor = None

        try:
            connection = self._get_connection()
            cursor = connection.cursor(cursor_factory=RealDictCursor)

            results = self._execute_pii_type_query(cursor, detector)

            if not results:
                logger.warning(
                    f"No PII type configurations found in database "
                    f"(detector={detector or 'ALL'}). "
                    "Will use default TOML configuration."
                )
                return None

            # Build dictionary keyed by PII type
            configs = {}
            for row in results:
                pii_type = row['pii_type']
                entry = {
                    'enabled': row['enabled'],
                    'threshold': float(row['threshold']),
                    'detector': row['detector'],
                    'category': row['category'],
                    'country_code': row['country_code'],
                    'detector_label': row['detector_label'],
                }
                # Primary key (may overwrite for duplicates across detectors)
                configs[pii_type] = entry
                # Composite key for precise per-detector lookup (always unique)
                if not detector:
                    configs[f"{row['detector']}:{pii_type}"] = entry

            logger.info(
                f"Successfully fetched {len(configs)} PII type configs from database "
                f"(detector={detector or 'ALL'})"
            )

            # Log sample of configs for debugging
            sample_types = list(configs.keys())[:3]
            for pii_type in sample_types:
                cfg = configs[pii_type]
                logger.debug(
                    f"  {pii_type}: enabled={cfg['enabled']}, "
                    f"threshold={cfg['threshold']}, detector={cfg['detector']}"
                )

            return configs

        except psycopg2.OperationalError as e:
            logger.exception(
                f"Database connection failed fetching PII type configs: {e}. "
                "Check DB_HOST, DB_PORT, DB_USER, DB_PASSWORD environment variables. "
                "Will use default TOML configuration."
            )
            return None

        except psycopg2.Error as e:
            logger.exception(
                f"Database query failed fetching PII type configs: {e}. "
                "Will use default TOML configuration."
            )
            return None

        except Exception as e:
            logger.exception(
                f"Unexpected error fetching PII type configs: {e}. "
                "Will use default TOML configuration."
            )
            return None

        finally:
            if cursor:
                cursor.close()
            if connection:
                connection.close()

    def update_ministral_concurrency(
        self, concurrency: int, tuned_signature: str
    ) -> bool:
        """Persist the auto-tuned Ministral concurrency and its host+model signature.

        Written once at startup by the concurrency auto-tuner (the only Python
        write path into ``pii_detection_config``). ``tuned_signature`` records the
        ``"host:port|model"`` the value was measured for, so a later startup can
        skip re-tuning when it is unchanged. Never raises: a failure is logged and
        returns ``False`` so a persistence hiccup never aborts service startup.

        Business Rule: single-row configuration table with id=1.

        Returns:
            True if the row was updated, False on any failure.
        """
        connection = None
        cursor = None
        try:
            connection = self._get_connection()
            cursor = connection.cursor()
            cursor.execute(
                """
                UPDATE pii_detection_config
                SET ministral_concurrency = %s,
                    ministral_concurrency_tuned_signature = %s,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = %s
                WHERE id = 1
                """,
                (int(concurrency), tuned_signature, "concurrency-autotuner"),
            )
            connection.commit()
            updated = cursor.rowcount > 0
            if updated:
                logger.info(
                    "Persisted auto-tuned ministral_concurrency=%d "
                    "(signature=%s)",
                    int(concurrency), tuned_signature,
                )
            else:
                logger.warning(
                    "update_ministral_concurrency matched no row (id=1 missing?)"
                )
            return updated
        except psycopg2.Error as e:
            logger.exception("Failed to persist auto-tuned concurrency: %s", e)
            if connection:
                connection.rollback()
            return False
        except Exception as e:  # pragma: no cover - defensive
            logger.exception("Unexpected error persisting concurrency: %s", e)
            if connection:
                connection.rollback()
            return False
        finally:
            if cursor:
                cursor.close()
            if connection:
                connection.close()

    def _execute_commit(
        self, sql: str, params: tuple, quiet_conn_errors: bool = False
    ) -> int:
        """Run a write statement, commit, return rowcount (-1 on failure, logged)."""
        rowcount = self._execute_and_commit(
            sql, params, quiet_conn_errors, lambda cursor: cursor.rowcount
        )
        return -1 if rowcount is None else rowcount

    def _execute_returning_one(
        self, sql: str, params: tuple, quiet_conn_errors: bool = False
    ) -> Optional[tuple]:
        """Run a statement yielding at most one row (``RETURNING`` or a SELECT),
        commit, return that row — ``None`` when nothing matched or on failure."""
        return self._execute_and_commit(
            sql,
            params,
            quiet_conn_errors,
            lambda cursor: cursor.fetchone() if cursor.rowcount > 0 else None,
        )

    def _execute_and_commit(
        self,
        sql: str,
        params: tuple,
        quiet_conn_errors: bool,
        read_result: Callable[[Any], Any],
    ) -> Any:
        """Execute ``sql``, read its outcome through ``read_result(cursor)``, commit.

        Never raises: DB write hiccups must not crash the poller/bench, so any
        failure is logged and reported as ``None``. ``quiet_conn_errors``
        downgrades connection-level failures to DEBUG — used by the
        benchmark-request poller, which runs every few seconds and would
        otherwise spam ERROR during a DB outage. Genuine query errors always
        stay at ERROR.
        """
        connection = None
        cursor = None
        try:
            connection = self._get_connection()
            cursor = connection.cursor()
            cursor.execute(sql, params)
            result = read_result(cursor)
            connection.commit()
            return result
        except psycopg2.OperationalError as e:
            if quiet_conn_errors:
                logger.debug("DB unavailable (poller, quiet): %s", e)
            else:
                logger.exception("DB write failed (connection): %s", e)
            if connection:
                connection.rollback()
            return None
        except psycopg2.Error as e:
            logger.exception("DB write failed: %s", e)
            if connection:
                connection.rollback()
            return None
        finally:
            if cursor:
                cursor.close()
            if connection:
                connection.close()

    def claim_bench_job(self) -> Optional[int]:
        """Atomically claim a pending on-demand benchmark request.

        Flips ``concurrency_bench_requested`` true -> false and the status to
        RUNNING in a single conditional UPDATE, so only the first poller tick that
        sees the request runs the bench (no double-run). Returns the highest
        concurrency the operator asked to measure, or ``None`` when there was no
        pending request (or the DB was unreachable).
        """
        row = self._execute_returning_one(
            """
            UPDATE pii_detection_config
            SET concurrency_bench_requested = false,
                concurrency_bench_status = 'RUNNING',
                concurrency_bench_progress = 0,
                concurrency_bench_message = 'Starting benchmark'
            WHERE id = 1 AND concurrency_bench_requested = true
            RETURNING concurrency_bench_max_concurrency
            """,
            (),
            quiet_conn_errors=True,
        )
        if row is None:
            return None
        return int(row[0]) if row[0] is not None else DEFAULT_BENCH_MAX_CONCURRENCY

    def update_bench_progress(self, progress: int, message: str) -> None:
        """Update the RUNNING benchmark progress percentage and label.

        Guarded on RUNNING so a cancellation written by the API
        (CANCEL_REQUESTED) is never overwritten by a late progress tick.
        """
        self._execute_commit(
            """
            UPDATE pii_detection_config
            SET concurrency_bench_progress = %s,
                concurrency_bench_message = %s
            WHERE id = 1 AND concurrency_bench_status = 'RUNNING'
            """,
            (int(progress), message),
        )

    def is_bench_cancel_requested(self) -> bool:
        """True once the operator asked to stop the running benchmark.

        Polled between chunk requests by the bench, so connection errors stay
        quiet and read as "not cancelled": a DB hiccup must not abort a run.
        """
        row = self._execute_returning_one(
            "SELECT concurrency_bench_status FROM pii_detection_config WHERE id = 1",
            (),
            quiet_conn_errors=True,
        )
        return row is not None and row[0] == "CANCEL_REQUESTED"

    def cancel_bench_job(self) -> None:
        """Mark the benchmark job CANCELLED; the stored concurrency is left as is."""
        self._execute_commit(
            """
            UPDATE pii_detection_config
            SET concurrency_bench_requested = false,
                concurrency_bench_status = 'CANCELLED',
                concurrency_bench_message = 'Benchmark cancelled by operator'
            WHERE id = 1
            """,
            (),
        )

    def complete_bench_job(self, concurrency: int, signature: str) -> None:
        """Persist the benchmark result and mark the job DONE (progress 100)."""
        self._execute_commit(
            """
            UPDATE pii_detection_config
            SET ministral_concurrency = %s,
                ministral_concurrency_tuned_signature = %s,
                concurrency_bench_status = 'DONE',
                concurrency_bench_progress = 100,
                concurrency_bench_message = %s,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = %s
            WHERE id = 1
            """,
            (
                int(concurrency),
                signature,
                f"Benchmark complete: concurrency={concurrency}",
                "concurrency-autotuner",
            ),
        )

    def fail_bench_job(self, message: str) -> None:
        """Mark the benchmark job FAILED with a human-readable reason."""
        self._execute_commit(
            """
            UPDATE pii_detection_config
            SET concurrency_bench_status = 'FAILED',
                concurrency_bench_message = %s
            WHERE id = 1
            """,
            (message,),
        )

    def _execute_pii_type_query(self, cursor, detector: Optional[str]):
        """Run the pii_type_config SELECT.

        All selected columns (including ``detector_label``, added by init-script
        007) are part of the base schema, so no optional-column fallback is
        required.
        """
        columns = (
            "pii_type, detector, enabled, threshold, "
            "category, country_code, detector_label"
        )
        where_clause = "WHERE detector = %s\n" if detector else ""
        params = (detector,) if detector else None
        query = (
            f"SELECT {columns}\n"
            f"FROM pii_type_config\n{where_clause}ORDER BY category, pii_type"
        )
        cursor.execute(query, params)
        return cursor.fetchall()


# Global singleton instance for reuse
_config_adapter = None


def get_database_config_adapter() -> DatabaseConfigAdapter:
    """Get or create the global DatabaseConfigAdapter instance."""
    global _config_adapter
    if _config_adapter is None:
        _config_adapter = DatabaseConfigAdapter()
    return _config_adapter
