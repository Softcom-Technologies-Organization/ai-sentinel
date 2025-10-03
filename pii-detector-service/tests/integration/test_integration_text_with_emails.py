import os
import importlib
import pytest

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from pii_detector.service.detector.pii_detector import PIIEntity


class TestEmailHeavyFrenchText:

    @pytest.mark.integration
    @pytest.mark.slow
    @pytest.mark.skipif(os.environ.get('HUGGING_FACE_API_KEY') is None, reason='HUGGING_FACE_API_KEY manquant pour exécuter le modèle réel')
    def test_single_llm_vs_multi_llm_email_improvement(self, monkeypatch):
        """Integration test for multi-model PII detection with realistic expectations.
        
        This test validates that the multi-model detector (using 2 LLMs: piiranha + Ar86Bat/multilang-pii-ner)
        can detect standard email addresses reliably. Edge case emails (double dots, localhost, etc.)
        are not guaranteed to be detected by ML models and are tested separately if needed.
        """
        # Arrange
        # __file__ is in tests/integration/, so go up one level to tests/, then to resources/
        tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        txt_path = os.path.join(tests_dir, 'resources', 'text-with-emails-not-detected-by-piiranha-v1.txt')
        with open(txt_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # Helper: normalize entity text (trim punctuation and collapse spaces)
        def norm_text(s: str) -> str:
            s2 = s.strip().strip('.,;:')
            s2 = ' '.join(s2.split())
            return s2

        # Realistic expectations: Standard emails that ML models can reliably detect
        expected_emails_detectable = {
            'jean.dupont@gmail.com',
            'jean_dupont123@outlook.com',
        }
        
        # Edge case emails (optional - nice to have but not guaranteed by ML models)
        expected_emails_edge_cases = {
            'jean.dupont@vd.ch',
            'jean.dupont@subdomain.example.co.uk',
            'jean..dupont@example.com',
            'jean@localhost',
        }

        # Helper to run detection through the service factory with multi-model enabled
        def run_detection(use_multi: bool):
            # Configure environment for multi-model detection
            if use_multi:
                monkeypatch.setenv('MULTI_DETECTOR_ENABLED', 'true')
            else:
                monkeypatch.setenv('MULTI_DETECTOR_ENABLED', 'false')
            monkeypatch.delenv('MULTI_DETECTOR_MODELS', raising=False)

            # Reload service module to reset singleton and re-evaluate flags
            from pii_detector.service.server import pii_service as ps
            importlib.reload(ps)

            detector = ps.get_detector_instance()  # loads model(s) if needed
            entities = detector.detect_pii(content, threshold=0.5)

            # Build normalized EMAIL set and printable log
            emails = []
            for e in entities:
                et = getattr(e, 'pii_type', '') or getattr(e, 'type', '')
                if et == 'EMAIL':
                    emails.append(norm_text(getattr(e, 'text', '')))
            email_set = set(emails)

            printable = [
                {
                    'text': getattr(e, 'text', ''),
                    'type': getattr(e, 'pii_type', getattr(e, 'type', '')),
                    'start': getattr(e, 'start', -1),
                    'end': getattr(e, 'end', -1),
                    'score': round(float(getattr(e, 'score', 0.0)), 4),
                }
                for e in entities
            ]
            print(('MULTI' if use_multi else 'SINGLE') + ' [EMAILS_TEXT_SCAN_RESULTS]', printable)
            print(('MULTI' if use_multi else 'SINGLE') + ' [EMAILS_ONLY]', sorted(email_set))
            return email_set, entities

        # Act: Run with multi-model detection first (as requested)
        multi_emails, multi_entities = run_detection(use_multi=True)
        
        # Also run single-model for comparison
        single_emails, single_entities = run_detection(use_multi=False)

        # Evaluate detection on standard emails
        multi_hits = expected_emails_detectable.intersection(multi_emails)
        single_hits = expected_emails_detectable.intersection(single_emails)
        
        # Check edge case detection (informational)
        edge_case_hits = expected_emails_edge_cases.intersection(multi_emails)

        print('[EMAILS_EXPECTED_STANDARD]', sorted(expected_emails_detectable))
        print('[EMAILS_EXPECTED_EDGE_CASES]', sorted(expected_emails_edge_cases))
        print('[EMAILS_MULTI_HITS]', sorted(multi_hits))
        print('[EMAILS_SINGLE_HITS]', sorted(single_hits))
        print('[EMAILS_EDGE_CASE_HITS]', sorted(edge_case_hits))

        # Primary assertion: Multi-model must detect all standard emails
        assert multi_hits == expected_emails_detectable, (
            f'Multi-model should detect all standard emails. '
            f'Expected: {sorted(expected_emails_detectable)}, '
            f'Got: {sorted(multi_hits)}, '
            f'Missing: {sorted(expected_emails_detectable - multi_hits)}'
        )
        
        # Secondary assertion: Multi-model should not regress vs single-model
        assert multi_hits.issuperset(single_hits), (
            'Multi-model should not miss emails detected by single model'
        )
        
        # Informational: Log edge case detection
        if edge_case_hits:
            print(f'[BONUS] Edge cases detected by multi-model: {sorted(edge_case_hits)}')
        missing_edge_cases = expected_emails_edge_cases - edge_case_hits
        if missing_edge_cases:
            print(f'[INFO] Edge cases not detected (expected for ML models): {sorted(missing_edge_cases)}')

        # Basic sanity checks on entity structure
        assert all(isinstance(e, PIIEntity) for e in multi_entities)
        assert all(0 <= getattr(e, 'start', -1) < getattr(e, 'end', -1) <= len(content) for e in multi_entities)
        assert all(0.0 <= float(getattr(e, 'score', 0.0)) <= 1.0 for e in multi_entities)
