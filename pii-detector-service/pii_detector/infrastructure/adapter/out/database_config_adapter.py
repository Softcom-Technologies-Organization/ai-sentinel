"""
Database adapter for fetching PII detection configuration from PostgreSQL.

This module provides a simple, reliable way to fetch the dynamic configuration
from the shared PostgreSQL database when requested via the gRPC fetch_config_from_db flag.
"""

import logging
import os
from typing import Optional

import psycopg2
from psycopg2.extras import RealDictCursor

logger = logging.getLogger(__name__)


class DatabaseConfigAdapter:
    """Adapter for fetching PII detection configuration from PostgreSQL."""

    def __init__(self):
        """Initialize database connection parameters from environment variables."""
        self.host = os.getenv("DB_HOST", "postgres")
        self.port = int(os.getenv("DB_PORT", "5432"))
        self.database = os.getenv("DB_NAME", "ai-sentinel")
        self.user = os.getenv("DB_USER", "postgres")
        self.password = os.getenv("DB_PASSWORD", "postgres")

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
            Dictionary with config keys: gliner_enabled, presidio_enabled,
            regex_enabled, default_threshold. Returns None if fetch fails.

        Business Rule: Single-row configuration table with id=1
        """
        connection = None
        cursor = None

        try:
            connection = self._get_connection()
            cursor = connection.cursor(cursor_factory=RealDictCursor)

            # Query the single-row configuration table
            query = """
                SELECT 
                    gliner_enabled,
                    presidio_enabled,
                    regex_enabled,
                    default_threshold
                FROM pii_detection_config
                WHERE id = 1
            """

            cursor.execute(query)
            result = cursor.fetchone()

            if result is None:
                logger.warning(
                    "No configuration found in database (id=1). "
                    "Will use default configuration from TOML file."
                )
                return None

            config = dict(result)
            logger.info(
                "Successfully fetched config from database: "
                f"gliner={config['gliner_enabled']}, "
                f"presidio={config['presidio_enabled']}, "
                f"regex={config['regex_enabled']}, "
                f"threshold={config['default_threshold']}"
            )
            return config

        except psycopg2.OperationalError as e:
            logger.error(
                f"Database connection failed: {e}. "
                "Check DB_HOST, DB_PORT, DB_USER, DB_PASSWORD environment variables. "
                "Will use default configuration from TOML file."
            )
            return None

        except psycopg2.Error as e:
            logger.error(
                f"Database query failed: {e}. "
                "Will use default configuration from TOML file."
            )
            return None

        except Exception as e:
            logger.error(
                f"Unexpected error fetching config: {e}. "
                "Will use default configuration from TOML file."
            )
            return None

        finally:
            if cursor:
                cursor.close()
            if connection:
                connection.close()


# Global singleton instance for reuse
_config_adapter = None


def get_database_config_adapter() -> DatabaseConfigAdapter:
    """Get or create the global DatabaseConfigAdapter instance."""
    global _config_adapter
    if _config_adapter is None:
        _config_adapter = DatabaseConfigAdapter()
    return _config_adapter
