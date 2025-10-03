import os
import pytest
from unittest.mock import patch, MagicMock

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from pii_detector.service.detector.pii_detector import PIIDetector, PIIEntity


SAMPLE_TEXT = (
    "08.03.2017/EV\n\n"
    "Procédure – Traitement des factures \n"
    "- Réception des factures par courrier, par e-mail ou en main propre de la part des \n\n"
    "collaborateurs (factures licences, matériel informatique, …) \n\n"
    "- Administration traite les factures : \n\n"
    "o Date du jour \n\n"
    "o Stempel \n\n"
    "o Nom de la société (Morphean, Softcom ou iCortex) \n\n"
    "o Date du traitement de la facture \n\n"
    "o N° du case (s’il y en a un) \n\n"
    "o Montant de la facture et la devise (si autre que CHF) \n\n"
    "o Une petite note manuscrite « Validée par (nom du collaborateur qui a commandé la \n\n"
    "marchandise) le (date de la validation) / visa » \n\n"
    "▪ ex :  Validée par Marine le 08.03.2017 / EV »\n\n"
    "o Stabilobosser : \n\n"
    "▪ Le n° de la facture \n\n"
    "▪ La date de la facture \n\n"
    "▪ Conditions de paiement \n\n"
    "- Les factures sont ensuite scannées et enregistrées sur notre serveur et/ou Dropbox pour \n\n"
    "Morphean en attente de validation de Rodrigue, Benoît et Vincent. \n\n"
    "- Les factures originales restent à l’Administration \n\n"
    "- Rodrigue, Benoît et Vincent traitent les factures une à deux fois par semaine. Dès qu’elles \n\n"
    "sont validées, ils passent les factures du dossier « A contrôler » à « Validées » \n\n"
    "- Dès que les factures sont dans le dossier « Validées », Administration les enregistre dans le \n\n"
    "dossier « Facture fournisseurs 2017 » correspondant. \n\n"
    "- Administration reprend toutes les factures originales validées, ajoute une note manuscrite \n\n"
    "Validée par (nom du responsable) le (date de la validation) / visa \n\n"
    "Validée par Mr Rouiller le 10.03.2017 / EV  \n\n"
    "- Envoi des factures à la fiduciaire par la poste. \n\n"
    "- La fiduciaire réceptionne, traite et saisi les factures dans l’eBanking \n\n"
    "- La fiduciaire envoi un mail à Rodrigue et Benoît lorsqu’il y a des factures à libérer sur \n\n"
    "l’eBanking \n\n"
    "- Rodrigue et Benoît libèrent les factures \n\n"
    "- La fiduciaire classe et archive les factures\n"
)


class TestRealScanFrenchText:

    @pytest.mark.integration
    @pytest.mark.slow
    @pytest.mark.skipif(os.environ.get('HUGGING_FACE_API_KEY') is None, reason='HUGGING_FACE_API_KEY manquant pour exécuter le modèle réel')
    def test_scan_demo_page_file_and_verify_expected_pii(self):
        """Integration test: scans tests/resources/page1_demo.txt and verifies expected PII.
        Logs all detected entities by the real model for visibility.
        """
        # Arrange
        # __file__ is in tests/integration/, so go up one level to tests/, then to resources/
        tests_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        demo_path = os.path.join(tests_dir, 'resources', 'page1_demo.txt')
        with open(demo_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        detector = PIIDetector()
        detector.load_model()

        # Act
        entities = detector.detect_pii(content, threshold=0.5)

        # Log results for visibility
        printable = [
            {
                'text': e.text,
                'type': e.pii_type,
                'start': e.start,
                'end': e.end,
                'score': round(e.score, 4),
            }
            for e in entities
        ]
        print('[DEMO_PAGE_REAL_MODEL_SCAN_RESULTS]', printable)

        # Normalize helper: trim punctuation and collapse spaces
        def norm_text(s: str) -> str:
            s2 = s.strip().strip('.,;:')
            s2 = ' '.join(s2.split())
            return s2

        # Build actual map of type -> set of normalized texts
        target_types = {
            'ACCOUNTNUM', 'BUILDINGNUM', 'CITY', 'CREDITCARDNUMBER', 'DATEOFBIRTH',
            'DRIVERLICENSENUM', 'EMAIL', 'GIVENNAME', 'IDCARDNUM', 'PASSWORD',
            'SOCIALNUM', 'STREET', 'SURNAME', 'TAXNUM', 'TELEPHONENUM', 'USERNAME', 'ZIPCODE'
        }
        actual = {t: set() for t in target_types}
        for e in entities:
            et = getattr(e, 'pii_type', '') or getattr(e, 'type', '')
            if et in actual:
                actual[et].add(norm_text(getattr(e, 'text', '')))

        expected = {
            'ACCOUNTNUM': {'123-456789-12'},
            'BUILDINGNUM': {'12','B2'},
            'CITY': {'Paris', 'Lyon'},
            'CREDITCARDNUMBER': {'4929 3412 8765 1234'},
            'DATEOFBIRTH': {'12/06/1984'},
            'DRIVERLICENSENUM': {'45678901234'},
            'EMAIL': {'martin.dupuis@example.com'},
            'GIVENNAME': {'Martin'},
            'IDCARDNUM': {'IDF-34982765'},
            'PASSWORD': {'P@ssword!2024'},
            'SOCIALNUM': {'1840678123456'},
            'STREET': {'Rue des Lilas'},
            'SURNAME': {'Dupuis'},
            'TAXNUM': {'TX-4578-9932'},
            'TELEPHONENUM': {'+33 6 45 78 12 34'},
            'USERNAME': {'mdupuis84'},
            'ZIPCODE': {'69007'},
        }

        # Additional per-type compact log
        compact_actual = {t: sorted(list(actual.get(t, set()))) for t in sorted(target_types)}
        print('[DEMO_PAGE_ACTUAL_BY_TYPE]', compact_actual)

        # Assertions per type
        for etype in sorted(target_types):
            exp = expected.get(etype, set())
            act = actual.get(etype, set())
            if exp != act:
                only_exp = sorted(list(exp - act))
                only_act = sorted(list(act - exp))
                print(f"[MISMATCH] {etype} only_expected={only_exp} only_actual={only_act}")
            else:
                if act:
                    print(f"[OK] {etype}: {sorted(list(act))}")
            assert act == exp, f"Mismatch for {etype}"

        # Sanity checks on entity structure
        assert all(isinstance(e, PIIEntity) for e in entities)
        assert all(0 <= e.start < e.end <= len(content) for e in entities)
        assert all(0.0 <= e.score <= 1.0 for e in entities)
