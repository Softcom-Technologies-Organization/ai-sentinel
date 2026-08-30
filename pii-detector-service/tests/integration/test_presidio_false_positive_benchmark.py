"""
Integration test: Presidio false-positive benchmark per label.

Runs the real Microsoft Presidio AnalyzerEngine against professional texts
that contain NO actual PII. Every detection is a false positive.

Goal: identify Presidio entity types that are unusable due to excessive
false positives at the 0.80 threshold.

Usage:
    pytest tests/integration/test_presidio_false_positive_benchmark.py -s -m integration
    python tests/integration/test_presidio_false_positive_benchmark.py
"""

import sys
import time
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional
from unittest.mock import patch, MagicMock

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from pii_detector.infrastructure.detector.presidio_detector import PresidioDetector

# ---------------------------------------------------------------------------
# Threshold used for all labels
# ---------------------------------------------------------------------------
THRESHOLD = 0.80

# ---------------------------------------------------------------------------
# Presidio entity registry (from data.sql)
# Maps our internal PII type -> Presidio entity type (detector_label)
# ---------------------------------------------------------------------------
PRESIDIO_ENTITIES: Dict[str, Dict] = {
    # Universal — Financial
    "CREDIT_CARD": {"entity": "CREDIT_CARD", "category": "Financial"},
    "IBAN_CODE": {"entity": "IBAN_CODE", "category": "Financial"},
    "CRYPTO": {"entity": "CRYPTO", "category": "Financial"},
    # Universal — Network
    "MAC_ADDRESS": {"entity": "MAC_ADDRESS", "category": "Network"},
    # Universal — Medical
    "MEDICAL_LICENSE": {"entity": "MEDICAL_LICENSE", "category": "Medical"},
    # Universal — Personal
    "NRP": {"entity": "NRP", "category": "Personal"},
    "EMAIL_ADDRESS": {"entity": "EMAIL_ADDRESS", "category": "Contact"},
    "PHONE_NUMBER": {"entity": "PHONE_NUMBER", "category": "Contact"},
    "URL": {"entity": "URL", "category": "Contact"},
    "IP_ADDRESS": {"entity": "IP_ADDRESS", "category": "Network"},
    "PERSON": {"entity": "PERSON", "category": "Personal"},
    "LOCATION": {"entity": "LOCATION", "category": "Location"},
    "DATE_TIME": {"entity": "DATE_TIME", "category": "Personal"},
    "AGE": {"entity": "AGE", "category": "Personal"},
    # USA
    "US_SSN": {"entity": "US_SSN", "category": "US", "country": "US"},
    "US_BANK_NUMBER": {"entity": "US_BANK_NUMBER", "category": "US", "country": "US"},
    "US_DRIVER_LICENSE": {"entity": "US_DRIVER_LICENSE", "category": "US", "country": "US"},
    "US_ITIN": {"entity": "US_ITIN", "category": "US", "country": "US"},
    "US_PASSPORT": {"entity": "US_PASSPORT", "category": "US", "country": "US"},
    # UK
    "UK_NHS": {"entity": "UK_NHS", "category": "UK", "country": "UK"},
    "UK_NINO": {"entity": "UK_NINO", "category": "UK", "country": "UK"},
    # Spain
    "ES_NIF": {"entity": "ES_NIF", "category": "ES", "country": "ES"},
    "ES_NIE": {"entity": "ES_NIE", "category": "ES", "country": "ES"},
    # Italy
    "IT_FISCAL_CODE": {"entity": "IT_FISCAL_CODE", "category": "IT", "country": "IT"},
    "IT_DRIVER_LICENSE": {"entity": "IT_DRIVER_LICENSE", "category": "IT", "country": "IT"},
    "IT_VAT_CODE": {"entity": "IT_VAT_CODE", "category": "IT", "country": "IT"},
    "IT_PASSPORT": {"entity": "IT_PASSPORT", "category": "IT", "country": "IT"},
    "IT_IDENTITY_CARD": {"entity": "IT_IDENTITY_CARD", "category": "IT", "country": "IT"},
    # Poland
    "PL_PESEL": {"entity": "PL_PESEL", "category": "PL", "country": "PL"},
    # Singapore
    "SG_NRIC_FIN": {"entity": "SG_NRIC_FIN", "category": "SG", "country": "SG"},
    "SG_UEN": {"entity": "SG_UEN", "category": "SG", "country": "SG"},
    # Australia
    "AU_ABN": {"entity": "AU_ABN", "category": "AU", "country": "AU"},
    "AU_ACN": {"entity": "AU_ACN", "category": "AU", "country": "AU"},
    "AU_TFN": {"entity": "AU_TFN", "category": "AU", "country": "AU"},
    "AU_MEDICARE": {"entity": "AU_MEDICARE", "category": "AU", "country": "AU"},
    # India
    "IN_PAN": {"entity": "IN_PAN", "category": "IN", "country": "IN"},
    "IN_AADHAAR": {"entity": "IN_AADHAAR", "category": "IN", "country": "IN"},
    "IN_VEHICLE_REGISTRATION": {"entity": "IN_VEHICLE_REGISTRATION", "category": "IN", "country": "IN"},
    "IN_VOTER": {"entity": "IN_VOTER", "category": "IN", "country": "IN"},
    "IN_PASSPORT": {"entity": "IN_PASSPORT", "category": "IN", "country": "IN"},
    # Finland
    "FI_PERSONAL_IDENTITY_CODE": {"entity": "FI_PERSONAL_IDENTITY_CODE", "category": "FI", "country": "FI"},
    # Korea
    "KR_RRN": {"entity": "KR_RRN", "category": "KR", "country": "KR"},
    # Thailand
    "TH_TNIN": {"entity": "TH_TNIN", "category": "TH", "country": "TH"},
}

# ---------------------------------------------------------------------------
# Realistic texts — content typical of Confluence/corporate docs that
# contains ambiguous tokens (product names, version numbers, ports, dates,
# locations, technical IDs, acronyms, code snippets) but NO actual PII.
# Shared corpus for comparable results.
# Every detection on these texts is a FALSE POSITIVE.
# ---------------------------------------------------------------------------
CLEAN_TEXTS: Dict[str, str] = {
    "deployment_procedure": (
        "Deployment procedure for DataBridge v3.2.1 on PREPROD2-VD environment. "
        "Connect to the bastion host via SSH on port 22. Run deploy.sh --env preprod2 "
        "to start the rollout. The service binds to 0.0.0.0:8443 and connects to "
        "the PostgreSQL cluster at db-internal:5432/databridge. Health check endpoint "
        "is available at /actuator/health. Rollback using tag 3.2.0-SNAPSHOT if needed. "
        "Container image: registry.gitlab.com/myorg/databridge:3.2.1-rc1. "
        "Expected memory footprint: 512Mi request, 1Gi limit. "
        "Kubernetes namespace: ns-databridge-preprod. Helm chart version 0.9.4. "
        "DNS entry: databridge.preprod.internal.vd.ch resolves to the ingress controller."
    ),
    "sprint_meeting_notes": (
        "Sprint 42 retrospective — 2025-03-15. Participants: DevOps team, QA chapter, "
        "Product Owner. Build time increased from 8 to 15 minutes after adding Sonar "
        "analysis. JIRA-4521: investigate Gradle build cache. JIRA-4522: split test "
        "suite into unit/integration modules. Target: reduce pipeline to under 10 min "
        "by Sprint 44. The Stockholm office reported flaky tests on Windows runners. "
        "Berlin team volunteered to investigate. Action items assigned in Confluence. "
        "Next planning session: Monday 2025-03-24 at 09:00 CET. Budget for new CI "
        "runners approved: CHF 12,500 per quarter."
    ),
    "infrastructure_wiki": (
        "Network topology — Production LAN. VLAN 100: application servers (10.0.1.0/24). "
        "VLAN 200: database tier (10.0.2.0/24). VLAN 300: management (10.0.3.0/24). "
        "Firewall rules: allow TCP 443 from VLAN 100 to VLAN 200. "
        "DNS servers: ns1.internal.vd.ch (10.0.3.10), ns2.internal.vd.ch (10.0.3.11). "
        "NTP source: ntp.ubuntu.com. DHCP range: 10.0.1.100–10.0.1.200. "
        "Switch model: Cisco Catalyst 9300 (serial C9300-24T). "
        "Rack location: Building B, Floor 3, Cabinet R12. "
        "UPS capacity: 20 kVA, runtime 45 minutes at full load. "
        "Monitoring via SNMP v3 on port 161. Community string rotated quarterly."
    ),
    "api_documentation": (
        "POST /api/v2/documents/scan HTTP/1.1. Content-Type: application/json. "
        "Authorization: Bearer <token>. Request body: {\"sourceType\": \"CONFLUENCE\", "
        "\"spaceKey\": \"IT-OPS\", \"pageId\": \"123456789\", \"threshold\": 0.80}. "
        "Response 200: {\"scanId\": \"a1b2c3d4-e5f6-7890-abcd-ef1234567890\", "
        "\"status\": \"RUNNING\", \"createdAt\": \"2025-03-15T10:30:00Z\"}. "
        "Error 401: invalid or expired bearer token. "
        "Error 429: rate limit exceeded (max 100 requests/minute). "
        "Pagination: use cursor=eyJwYWdlIjoyLCJzaXplIjoxMH0= for next page. "
        "Webhook callback URL: https://hooks.internal.vd.ch/pii-results. "
        "Timeout: 30000ms. Retry policy: exponential backoff, max 3 attempts."
    ),
    "incident_report": (
        "Incident INC-2025-0342 — Severity P2 — 2025-03-10 08:15 UTC. "
        "The PDF Extractor service returned HTTP 503 for 23 minutes. Root cause: "
        "JVM heap exhaustion on pod pdf-extractor-7f8b9c6d4-xk2lm (node worker-03). "
        "Memory limit was 2Gi but processing a 450-page document required 2.8Gi. "
        "Mitigation: increased memory limit to 4Gi, added circuit breaker with "
        "max-document-size=200 pages. Permanent fix: stream-based processing (JIRA-4601). "
        "Impact: 142 scan requests queued, 0 data loss. MTTR: 23 minutes. "
        "Timeline: 08:15 alert fired, 08:22 on-call acknowledged, 08:38 fix deployed. "
        "Post-mortem review scheduled for 2025-03-12 14:00 CET."
    ),
    "confluence_onboarding": (
        "Welcome to the IT Operations team! Here is your onboarding checklist. "
        "Step 1: Request VPN access through the ServiceNow portal (catalog item REQ-VPN-001). "
        "Step 2: Install IntelliJ IDEA 2025.1 and configure the Maven settings.xml with "
        "the Nexus mirror at https://nexus.internal.vd.ch/repository/maven-public/. "
        "Step 3: Clone the repositories from GitLab (group: /myorg/platform). "
        "Step 4: Set up Docker Desktop and authenticate to the container registry. "
        "Step 5: Join Slack channels #dev-backend, #ops-alerts, and #team-lunch. "
        "Step 6: Complete the security awareness training module SEC-101 by end of week 2. "
        "Office location: Lausanne, Avenue de Cour 135, Building C, 3rd floor. "
        "WiFi network: CORP-SECURE (WPA3 Enterprise, certificate-based auth)."
    ),
    "architecture_decision": (
        "ADR-017: Switch from MongoDB 6.0 to PostgreSQL 16 for the reporting service. "
        "Status: Accepted. Date: 2025-02-28. Context: ACID transactions required for "
        "financial audit trails. MongoDB's eventual consistency model caused reconciliation "
        "errors in Q4 2024 (see INC-2024-0891). Decision: migrate to PostgreSQL 16 with "
        "JSONB columns for semi-structured data. Consequences: estimated migration effort "
        "3 weeks (Sprint 45–46). Connection pool: HikariCP, max-pool-size=20. "
        "Read replicas for reporting queries. Flyway for schema versioning. "
        "License: PostgreSQL License (permissive, similar to MIT). "
        "Alternative considered: CockroachDB — rejected due to operational complexity "
        "and licensing costs ($45,000/year for enterprise tier)."
    ),
    "security_hardening_guide": (
        "Security hardening checklist for Spring Boot applications. "
        "TLS: enforce HTTPS with TLS 1.3 minimum. Disable TLS 1.0 and 1.1. "
        "Certificate: issued by Let's Encrypt or internal CA (validity 90 days). "
        "Headers: set X-Content-Type-Options: nosniff, X-Frame-Options: DENY, "
        "Strict-Transport-Security: max-age=31536000. "
        "CORS: restrict to https://*.internal.vd.ch. "
        "Authentication: OAuth 2.0 with Keycloak. Token lifetime: access=5min, refresh=30min. "
        "Password policy: minimum 12 characters, must include uppercase, lowercase, digit, "
        "and special character. Account lockout after 5 failed attempts for 15 minutes. "
        "Secrets management: use HashiCorp Vault or Infisical. Never store credentials "
        "in application.yml, environment variables, or Confluence pages. "
        "Logging: never log tokens, passwords, or PII. Use MDC for correlation IDs."
    ),
    "release_notes": (
        "Release notes — AI Sentinel v2.4.0 — 2025-03-20. "
        "New features: SharePoint connector (Beta), Confluence Data Center support, "
        "bulk export of scan results in CSV format. "
        "Improvements: detection throughput improved by 35% via parallel chunk "
        "processing with ThreadPoolExecutor (max_workers=10). Presidio analyzer now "
        "supports French and German languages. Dashboard refresh rate reduced from "
        "5s to 2s using Server-Sent Events. "
        "Bug fixes: JIRA-4480 — false positive on version numbers like 2.3.1 detected "
        "as IP addresses. JIRA-4495 — scan status stuck at RUNNING when Confluence "
        "returns 429. JIRA-4501 — PDF attachment download fails for files > 50MB. "
        "Known issues: JIRA-4510 — username detection threshold may need tuning for "
        "short identifiers (3-4 characters). "
        "Upgrade path: run Flyway migration V2.4.0__add_source_type_sharepoint.sql."
    ),
    "project_budget_report": (
        "Q1 2025 Budget Report — AI Sentinel Platform. "
        "Infrastructure costs: Azure AKS cluster CHF 8,200/month (3 nodes, D4s v5). "
        "Database: Azure Database for PostgreSQL CHF 1,450/month (GP_Gen5_4 tier). "
        "Storage: 2 TB blob storage CHF 120/month. "
        "Licenses: GitLab Ultimate CHF 1,200/year (15 seats), SonarQube Developer "
        "CHF 3,500/year, Infisical Team CHF 600/year. "
        "Personnel: 4.5 FTE allocated (2 backend, 1.5 frontend, 1 DevOps). "
        "Training budget: CHF 5,000 for Spring Boot 3 certification and Angular 19 workshop. "
        "Total Q1 spend: CHF 42,350 (92% of budget). Variance: -CHF 3,650 under budget. "
        "Forecast Q2: CHF 48,000 (+13%) due to SharePoint integration and GPU node "
        "for model serving (NC6s v3, CHF 2,800/month)."
    ),
}


@dataclass
class EntityResult:
    """Result of testing a single Presidio entity type against all clean texts."""
    entity_type: str
    category: str
    country: Optional[str]
    total_false_positives: int = 0
    false_positives_by_text: Dict[str, int] = field(default_factory=dict)
    detections: List[Dict] = field(default_factory=list)
    elapsed_seconds: float = 0.0


def _run_single_entity(
    analyzer,
    entity_type: str,
    category: str,
    country: Optional[str],
    threshold: float,
    language: str = "en",
) -> EntityResult:
    """Run a single Presidio entity type against all clean texts."""
    result = EntityResult(
        entity_type=entity_type,
        category=category,
        country=country,
    )
    start = time.time()

    for text_name, text in CLEAN_TEXTS.items():
        raw_results = analyzer.analyze(
            text=text,
            language=language,
            entities=[entity_type],
            score_threshold=threshold,
            return_decision_process=False,
        )
        count = len(raw_results)
        result.false_positives_by_text[text_name] = count
        result.total_false_positives += count
        for r in raw_results:
            result.detections.append({
                "text_source": text_name,
                "detected_text": text[r.start:r.end],
                "score": r.score,
                "start": r.start,
                "end": r.end,
            })

    result.elapsed_seconds = time.time() - start
    return result


def _print_false_positive_details(ranked: List[EntityResult]) -> None:
    """Print detailed false-positive log for problematic entity types."""
    problematic = [r for r in ranked if r.total_false_positives > 0]
    if not problematic:
        return
    print("\n" + "=" * 100)
    print("DETAILED FALSE POSITIVES")
    print("=" * 100)
    for r in problematic:
        print(f"\n[{r.entity_type}] category={r.category} — {r.total_false_positives} FP")
        for d in r.detections:
            print(
                f"   Source: {d['text_source']:<35} "
                f"Text: '{d['detected_text']:<40}' "
                f"Score: {d['score']:.3f}"
            )


def _print_summary(
    results: List[EntityResult],
    unusable: List[EntityResult],
    warning: List[EntityResult],
    clean_labels: List[EntityResult],
) -> None:
    """Print summary section of the Presidio benchmark report."""
    print("\n" + "=" * 100)
    print("SUMMARY")
    print("=" * 100)
    total_fp = sum(r.total_false_positives for r in results)
    print(f"   Total false positives:  {total_fp}")
    print(f"   UNUSABLE entities (>=5):  {len(unusable)}")
    print(f"   WARNING entities (2-4):   {len(warning)}")
    print(f"   CLEAN entities (0-1):     {len(clean_labels)}")
    print()

    if unusable:
        print("   UNUSABLE entities to consider disabling:")
        for r in unusable:
            print(f"      - {r.entity_type:<35} ({r.total_false_positives} FP) [{r.category}]")

    if warning:
        print("\n   WARNING entities to monitor:")
        for r in warning:
            print(f"      - {r.entity_type:<35} ({r.total_false_positives} FP) [{r.category}]")

    print("\n" + "-" * 100)
    print("FALSE POSITIVES BY CATEGORY:")
    by_category: Dict[str, int] = defaultdict(int)
    for r in results:
        by_category[r.category] += r.total_false_positives
    for cat, fp in sorted(by_category.items(), key=lambda x: x[1], reverse=True):
        bar = "#" * fp
        print(f"   {cat:<15} {fp:>4} FP  {bar}")

    print("\n" + "=" * 100)


def _print_report(results: List[EntityResult]) -> None:
    """Print a human-readable benchmark report."""
    print("\n" + "=" * 100)
    print("PRESIDIO FALSE-POSITIVE BENCHMARK REPORT")
    print(f"Threshold: {THRESHOLD} | Clean texts: {len(CLEAN_TEXTS)} | Entities tested: {len(results)}")
    print("=" * 100)

    ranked = sorted(results, key=lambda r: r.total_false_positives, reverse=True)

    print(f"\n{'Entity Type':<35} {'Category':<12} {'Country':<8} {'FP':>4} {'Verdict':>12}")
    print("-" * 100)

    unusable = []
    warning = []
    clean_labels = []

    for r in ranked:
        if r.total_false_positives >= 5:
            verdict = "UNUSABLE"
            unusable.append(r)
        elif r.total_false_positives >= 2:
            verdict = "WARNING"
            warning.append(r)
        else:
            verdict = "OK" if r.total_false_positives == 0 else "MINOR"
            clean_labels.append(r)

        country_str = r.country or "-"
        print(
            f"{r.entity_type:<35} {r.category:<12} {country_str:<8} "
            f"{r.total_false_positives:>4} {verdict:>12}"
        )

    _print_false_positive_details(ranked)
    _print_summary(results, unusable, warning, clean_labels)


@pytest.fixture(scope="module")
def presidio_analyzer():
    """Load Presidio AnalyzerEngine once for all tests in this module."""
    print("\n[SETUP] Loading Presidio analyzer...")

    # Mock DB adapter to avoid PostgreSQL dependency
    with patch(
        "pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter"
    ) as mock_get:
        mock_adapter = MagicMock()
        mock_adapter.fetch_pii_type_configs.return_value = {}
        mock_get.return_value = mock_adapter

        detector = PresidioDetector()
        detector.load_model()

    print("[SETUP] Presidio analyzer loaded successfully")
    return detector._analyzer


@pytest.mark.integration
@pytest.mark.slow
def test_presidio_false_positive_benchmark(presidio_analyzer):
    """
    Benchmark each Presidio entity type for false positives on clean texts.

    For each of the 42 Presidio entity types:
    1. Run analysis with ONLY that entity enabled against 10 clean texts
    2. Count every detection as a false positive
    3. Produce a ranked report identifying unusable entity types

    Entity types with >= 5 false positives are flagged UNUSABLE.
    Entity types with 2-4 false positives are flagged WARNING.
    """
    print("\n" + "=" * 100)
    print("STARTING PRESIDIO FALSE-POSITIVE BENCHMARK")
    print(f"Entities: {len(PRESIDIO_ENTITIES)} | Texts: {len(CLEAN_TEXTS)} | Threshold: {THRESHOLD}")
    print("=" * 100)

    results: List[EntityResult] = []
    unsupported: List[str] = []
    supported_entities = set(presidio_analyzer.get_supported_entities(language="en"))
    total_start = time.time()

    for idx, (pii_type, config) in enumerate(PRESIDIO_ENTITIES.items(), 1):
        entity = config["entity"]
        category = config["category"]
        country = config.get("country")

        print(f"   [{idx:2}/{len(PRESIDIO_ENTITIES)}] Testing '{entity}'...", end="", flush=True)

        if entity not in supported_entities:
            unsupported.append(entity)
            print(" SKIPPED (no recognizer for 'en')")
            continue

        result = _run_single_entity(
            analyzer=presidio_analyzer,
            entity_type=entity,
            category=category,
            country=country,
            threshold=THRESHOLD,
        )
        results.append(result)

        status = "OK" if result.total_false_positives == 0 else f"{result.total_false_positives} FP"
        print(f" {status} ({result.elapsed_seconds:.2f}s)")

    total_elapsed = time.time() - total_start
    print(f"\nBenchmark completed in {total_elapsed:.1f}s")

    if unsupported:
        print(f"Entities without an 'en' recognizer (not benchmarked): {', '.join(unsupported)}")

    _print_report(results)

    # Soft assertion: log but don't fail — the report IS the deliverable
    total_fp = sum(r.total_false_positives for r in results)
    unusable_count = sum(1 for r in results if r.total_false_positives >= 5)
    print(f"\n[ASSERT] Total FP: {total_fp}, Unusable entities: {unusable_count}")


if __name__ == "__main__":
    print("Loading Presidio analyzer...")

    with patch(
        "pii_detector.infrastructure.adapter.out.database_config_adapter.get_database_config_adapter"
    ) as mock_get:
        mock_adapter = MagicMock()
        mock_adapter.fetch_pii_type_configs.return_value = {}
        mock_get.return_value = mock_adapter

        det = PresidioDetector()
        det.load_model()

    print("Analyzer loaded.\n")
    test_presidio_false_positive_benchmark(det._analyzer)
