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

    def fetch_pii_type_configs(self, detector: str = None) -> Optional[dict]:
        """
        Fetch PII type-specific configurations from database.

        Args:
            detector: Optional detector filter ('GLINER', 'PRESIDIO', 'REGEX').
                     If None, fetches all PII type configs.

        Returns:
            Dictionary mapping PII type to config dict with keys:
            - enabled (bool): Whether this PII type is enabled
            - threshold (float): Detection threshold for this type (0.0-1.0)
            - detector (str): Detector name (GLINER, PRESIDIO, REGEX)
            - display_name (str): Human-readable name
            - category (str): PII category
            
            Example: {
                'EMAIL': {'enabled': True, 'threshold': 0.5, 'detector': 'GLINER', ...},
                'CREDIT_CARD': {'enabled': False, 'threshold': 0.7, 'detector': 'PRESIDIO', ...}
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

            # Build query with optional detector filter
            if detector:
                self.logger.info(f"Detector filter: {detector}")
                query = """
                    SELECT 
                        pii_type,
                        detector,
                        enabled,
                        threshold,
                        display_name,
                        description,
                        category,
                        country_code,
                        detector_label
                    FROM pii_type_config
                    WHERE detector = %s
                    ORDER BY category, pii_type
                """
                cursor.execute(query, (detector,))
            else:
                self.logger.info("No Detector")
                query = """
                    SELECT 
                        pii_type,
                        detector,
                        enabled,
                        threshold,
                        display_name,
                        description,
                        category,
                        country_code,
                        detector_label
                    FROM pii_type_config
                    ORDER BY category, pii_type
                """
                cursor.execute(query)

            results = cursor.fetchall()

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
                configs[pii_type] = {
                    'enabled': row['enabled'],
                    'threshold': float(row['threshold']),
                    'detector': row['detector'],
                    'display_name': row['display_name'],
                    'description': row['description'],
                    'category': row['category'],
                    'country_code': row['country_code'],
                    'detector_label': row['detector_label']
                }

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
            logger.error(
                f"Database connection failed fetching PII type configs: {e}. "
                "Check DB_HOST, DB_PORT, DB_USER, DB_PASSWORD environment variables. "
                "Will use default TOML configuration."
            )
            return None

        except psycopg2.Error as e:
            logger.error(
                f"Database query failed fetching PII type configs: {e}. "
                "Will use default TOML configuration."
            )
            return None

        except Exception as e:
            logger.error(
                f"Unexpected error fetching PII type configs: {e}. "
                "Will use default TOML configuration."
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
