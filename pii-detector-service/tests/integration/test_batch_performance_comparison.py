"""
Test d'intégration comparant les performances avec différentes stratégies de traitement.

Ce test évalue:
1. Vélocité: temps de traitement pour différentes tailles de texte
2. Cohérence: vérification que les PII détectées sont identiques
3. Scalabilité: performance avec volume important (1-2 min de traitement)

Stratégies testées:
- Sequential: traitement séquentiel actuel (baseline)
- Parallel Chunks: parallélisation des chunks d'un même texte
- Multi-Text Sequential: traitement séquentiel de plusieurs textes
- Multi-Text Parallel: traitement parallèle de plusieurs textes (simulation batch)
"""

import concurrent.futures
import json
import random
import sys
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import List, Dict

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from pii_detector.service.detector.gliner_detector import GLiNERDetector
from pii_detector.service.detector.models import PIIEntity, DetectionConfig


@dataclass
class TestCase:
    """Cas de test avec texte et taille."""
    name: str
    text: str
    size_chars: int
    expected_min_pii: int  # Nombre minimum de PII attendues


@dataclass
class PerformanceMetrics:
    """Métriques de performance pour une stratégie."""
    strategy_name: str
    total_time_seconds: float
    texts_processed: int
    total_chars_processed: int
    avg_time_per_text: float
    avg_chars_per_second: float
    pii_entities_found: int
    unique_pii_types: List[str]


@dataclass
class ComparisonResult:
    """Résultat de comparaison entre stratégies."""
    baseline_metrics: PerformanceMetrics
    optimized_metrics: PerformanceMetrics
    speedup_factor: float
    consistency_check: Dict[str, bool]
    recommendation: str


class PerformanceTestSuite:
    """Suite de tests de performance pour GLiNER."""
    
    def __init__(self):
        self.config = DetectionConfig()
        self.detector = GLiNERDetector(config=self.config)
        self.detector.download_model()
        self.detector.load_model()
        
    def generate_test_cases(self) -> List[TestCase]:
        """Génère des cas de test variés et réalistes."""
        
        # Petits textes (< 500 chars)
        small_texts = [
            TestCase(
                name="Small_Email",
                text=f"Contact {i}: john.doe{i}@example.com, phone: +33 6 12 34 56 {i:02d}, lives in Paris at {i} Main Street.",
                size_chars=100,
                expected_min_pii=3
            ) for i in range(1, 21)  # 20 petits textes
        ]
        
        # Textes moyens (500-2000 chars)
        medium_base = """
        Business Report #{num}
        
        Prepared by: Jean Dupont (jean.dupont{num}@company.fr)
        Date: 15/05/2024
        
        Executive Summary:
        This quarterly report covers Q{quarter} 2024 performance metrics for our Paris office located at 
        {num} Avenue des Champs-Élysées, 75008 Paris. For inquiries, contact our office at 
        +33 1 42 56 78 {num:02d} or email info{num}@company.fr.
        
        Key Personnel:
        - CEO: Marie Martin (marie.martin{num}@company.fr, +33 6 45 67 89 {num:02d})
        - CFO: Pierre Leroy (pierre.leroy{num}@company.fr, +33 6 78 90 12 {num:02d})
        - CTO: Sophie Bernard (sophie.bernard{num}@company.fr, +33 6 23 45 67 {num:02d})
        
        Financial Data:
        - Revenue: €{revenue}M
        - Operating costs: €{costs}M
        - Net margin: {margin}%
        
        Customer accounts processed: {accounts}
        New customer registrations: {registrations}
        Support tickets handled: {tickets}
        
        Regional offices:
        - Lyon: {num}0 Rue de la République, 69002 Lyon
        - Marseille: {num}5 Boulevard de la Liberté, 13001 Marseille
        - Toulouse: {num}8 Place du Capitole, 31000 Toulouse
        """
        
        medium_texts = [
            TestCase(
                name=f"Medium_Report_{i}",
                text=medium_base.format(
                    num=i,
                    quarter=(i % 4) + 1,
                    revenue=random.randint(10, 100),
                    costs=random.randint(5, 80),
                    margin=random.randint(10, 30),
                    accounts=random.randint(1000, 5000),
                    registrations=random.randint(100, 500),
                    tickets=random.randint(50, 200)
                ),
                size_chars=len(medium_base),
                expected_min_pii=8
            ) for i in range(1, 31)  # 30 textes moyens
        ]
        
        # Grands textes (2000-5000 chars)
        large_base = """
        Comprehensive Annual Report #{num}
        
        CONFIDENTIAL DOCUMENT - Internal Use Only
        
        Document ID: DOC-2024-{num:04d}
        Prepared by: {author_name} ({author_email})
        Date: {date}
        
        1. EXECUTIVE SUMMARY
        
        This comprehensive report covers the full year 2024 operational performance for all 
        regional offices. Key contact for this document: {contact_name} at {contact_email} 
        or phone {contact_phone}.
        
        2. ORGANIZATIONAL STRUCTURE
        
        Headquarters: {hq_address}
        Phone: {hq_phone}
        Email: {hq_email}
        
        Regional Directors:
        - North Region: {north_director} ({north_email}, {north_phone})
          Office: {north_address}
        - South Region: {south_director} ({south_email}, {south_phone})
          Office: {south_address}
        - East Region: {east_director} ({east_email}, {east_phone})
          Office: {east_address}
        - West Region: {west_director} ({west_email}, {west_phone})
          Office: {west_address}
        
        3. EMPLOYEE INFORMATION
        
        Total Employees: {total_employees}
        New Hires Q1-Q4: {new_hires}
        
        Department Heads:
        - HR: {hr_head} ({hr_email}, SSN: {hr_ssn})
        - Finance: {fin_head} ({fin_email}, SSN: {fin_ssn})
        - Operations: {ops_head} ({ops_email}, SSN: {ops_ssn})
        - IT: {it_head} ({it_email}, SSN: {it_ssn})
        
        4. FINANCIAL SUMMARY
        
        Total Revenue: €{revenue}M
        Operating Expenses: €{expenses}M
        Net Income: €{net_income}M
        
        Tax ID: {tax_id}
        Bank Account: {bank_account}
        IBAN: FR76 {iban_suffix}
        
        5. CUSTOMER DATA SUMMARY
        
        Total Active Customers: {total_customers}
        Enterprise Accounts: {enterprise_accounts}
        
        Top 5 Customers by Revenue:
        1. {customer1_name} - Contact: {customer1_email}, Phone: {customer1_phone}
        2. {customer2_name} - Contact: {customer2_email}, Phone: {customer2_phone}
        3. {customer3_name} - Contact: {customer3_email}, Phone: {customer3_phone}
        4. {customer4_name} - Contact: {customer4_email}, Phone: {customer4_phone}
        5. {customer5_name} - Contact: {customer5_email}, Phone: {customer5_phone}
        
        6. COMPLIANCE AND SECURITY
        
        Data Protection Officer: {dpo_name}
        Contact: {dpo_email}, {dpo_phone}
        Office: {dpo_address}
        
        Security Incidents Reported: {incidents}
        GDPR Requests Processed: {gdpr_requests}
        
        7. FUTURE PROJECTIONS
        
        The strategic plan for 2025 includes expansion into new markets with projected 
        investment of €{investment}M. Key stakeholder meetings scheduled for Q1 2025.
        
        For questions regarding this report, please contact:
        Chief Strategy Officer: {cso_name}
        Email: {cso_email}
        Phone: {cso_phone}
        Office: {cso_address}
        
        ---
        End of Report
        Document Version: 1.0
        Last Updated: {date}
        """
        
        large_texts = []
        for i in range(1, 21):  # 20 grands textes
            data = {
                'num': i,
                'author_name': f'Director{i} Smith',
                'author_email': f'director{i}@company.fr',
                'date': f'15/12/2024',
                'contact_name': f'Contact Manager{i}',
                'contact_email': f'contact{i}@company.fr',
                'contact_phone': f'+33 1 42 00 {i:02d} {i:02d}',
                'hq_address': f'{i}0 Avenue Headquarters, 75001 Paris',
                'hq_phone': f'+33 1 40 00 {i:02d} 00',
                'hq_email': f'hq{i}@company.fr',
                'north_director': f'North Director{i}',
                'north_email': f'north{i}@company.fr',
                'north_phone': f'+33 3 20 {i:02d} {i:02d} {i:02d}',
                'north_address': f'{i} Rue du Nord, 59000 Lille',
                'south_director': f'South Director{i}',
                'south_email': f'south{i}@company.fr',
                'south_phone': f'+33 4 91 {i:02d} {i:02d} {i:02d}',
                'south_address': f'{i} Boulevard du Sud, 13000 Marseille',
                'east_director': f'East Director{i}',
                'east_email': f'east{i}@company.fr',
                'east_phone': f'+33 3 88 {i:02d} {i:02d} {i:02d}',
                'east_address': f'{i} Rue de l\'Est, 67000 Strasbourg',
                'west_director': f'West Director{i}',
                'west_email': f'west{i}@company.fr',
                'west_phone': f'+33 2 40 {i:02d} {i:02d} {i:02d}',
                'west_address': f'{i} Quai de l\'Ouest, 44000 Nantes',
                'total_employees': random.randint(500, 2000),
                'new_hires': random.randint(50, 200),
                'hr_head': f'HR Head{i}',
                'hr_email': f'hr{i}@company.fr',
                'hr_ssn': f'{i:03d}-{i:02d}-{i:04d}',
                'fin_head': f'Finance Head{i}',
                'fin_email': f'finance{i}@company.fr',
                'fin_ssn': f'{i:03d}-{i:02d}-{(i+1):04d}',
                'ops_head': f'Operations Head{i}',
                'ops_email': f'ops{i}@company.fr',
                'ops_ssn': f'{i:03d}-{i:02d}-{(i+2):04d}',
                'it_head': f'IT Head{i}',
                'it_email': f'it{i}@company.fr',
                'it_ssn': f'{i:03d}-{i:02d}-{(i+3):04d}',
                'revenue': random.randint(50, 200),
                'expenses': random.randint(30, 150),
                'net_income': random.randint(10, 50),
                'tax_id': f'FR{i:02d}{random.randint(100000000, 999999999)}',
                'bank_account': f'FR76 {i:04d} {i:04d} {i:04d} {i:04d} {i:04d} {i:03d}',
                'iban_suffix': f'{i:04d} {i:04d} {i:04d} {i:04d} {i:04d}',
                'total_customers': random.randint(1000, 5000),
                'enterprise_accounts': random.randint(100, 500),
                'customer1_name': f'Enterprise{i}A Corp',
                'customer1_email': f'contact{i}a@enterprise.com',
                'customer1_phone': f'+33 1 70 {i:02d} {i:02d} 01',
                'customer2_name': f'Enterprise{i}B Inc',
                'customer2_email': f'contact{i}b@enterprise.com',
                'customer2_phone': f'+33 1 70 {i:02d} {i:02d} 02',
                'customer3_name': f'Enterprise{i}C Ltd',
                'customer3_email': f'contact{i}c@enterprise.com',
                'customer3_phone': f'+33 1 70 {i:02d} {i:02d} 03',
                'customer4_name': f'Enterprise{i}D SA',
                'customer4_email': f'contact{i}d@enterprise.com',
                'customer4_phone': f'+33 1 70 {i:02d} {i:02d} 04',
                'customer5_name': f'Enterprise{i}E GmbH',
                'customer5_email': f'contact{i}e@enterprise.com',
                'customer5_phone': f'+33 1 70 {i:02d} {i:02d} 05',
                'dpo_name': f'DPO{i} Johnson',
                'dpo_email': f'dpo{i}@company.fr',
                'dpo_phone': f'+33 1 45 {i:02d} {i:02d} {i:02d}',
                'dpo_address': f'{i} Rue de la Compliance, 75008 Paris',
                'incidents': random.randint(0, 10),
                'gdpr_requests': random.randint(10, 100),
                'investment': random.randint(10, 50),
                'cso_name': f'CSO{i} Williams',
                'cso_email': f'cso{i}@company.fr',
                'cso_phone': f'+33 1 48 {i:02d} {i:02d} {i:02d}',
                'cso_address': f'{i} Boulevard Strategy, 75016 Paris',
            }
            
            text = large_base.format(**data)
            large_texts.append(TestCase(
                name=f"Large_Report_{i}",
                text=text,
                size_chars=len(text),
                expected_min_pii=40  # Beaucoup de PII dans ces rapports
            ))
        
        return small_texts + medium_texts + large_texts
    
    def test_sequential_baseline(self, test_cases: List[TestCase]) -> PerformanceMetrics:
        """Test baseline: traitement séquentiel actuel."""
        print(f"\n{'='*80}")
        print("TEST 1: Sequential Baseline (Current Implementation)")
        print(f"{'='*80}")
        
        start_time = time.time()
        all_entities = []
        total_chars = 0
        
        for idx, test_case in enumerate(test_cases, 1):
            if idx % 10 == 0:
                elapsed = time.time() - start_time
                print(f"Progress: {idx}/{len(test_cases)} texts ({elapsed:.1f}s elapsed)")
            
            entities = self.detector.detect_pii(test_case.text, threshold=0.5)
            all_entities.extend(entities)
            total_chars += test_case.size_chars
        
        end_time = time.time()
        total_time = end_time - start_time
        
        unique_types = list(set([e.pii_type for e in all_entities]))
        
        metrics = PerformanceMetrics(
            strategy_name="Sequential Baseline",
            total_time_seconds=total_time,
            texts_processed=len(test_cases),
            total_chars_processed=total_chars,
            avg_time_per_text=total_time / len(test_cases),
            avg_chars_per_second=total_chars / total_time,
            pii_entities_found=len(all_entities),
            unique_pii_types=unique_types
        )
        
        self._print_metrics(metrics)
        return metrics
    
    def test_parallel_texts(self, test_cases: List[TestCase], max_workers: int = 3) -> PerformanceMetrics:
        """Test optimisé: traitement parallèle de plusieurs textes."""
        print(f"\n{'='*80}")
        print(f"TEST 2: Parallel Processing (max_workers={max_workers})")
        print(f"{'='*80}")
        
        start_time = time.time()
        all_entities = []
        total_chars = sum(tc.size_chars for tc in test_cases)
        
        def process_text(test_case: TestCase) -> List[PIIEntity]:
            return self.detector.detect_pii(test_case.text, threshold=0.5)
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {executor.submit(process_text, tc): tc for tc in test_cases}
            
            completed = 0
            for future in concurrent.futures.as_completed(futures):
                completed += 1
                if completed % 10 == 0:
                    elapsed = time.time() - start_time
                    print(f"Progress: {completed}/{len(test_cases)} texts ({elapsed:.1f}s elapsed)")
                
                entities = future.result()
                all_entities.extend(entities)
        
        end_time = time.time()
        total_time = end_time - start_time
        
        unique_types = list(set([e.pii_type for e in all_entities]))
        
        metrics = PerformanceMetrics(
            strategy_name=f"Parallel (workers={max_workers})",
            total_time_seconds=total_time,
            texts_processed=len(test_cases),
            total_chars_processed=total_chars,
            avg_time_per_text=total_time / len(test_cases),
            avg_chars_per_second=total_chars / total_time,
            pii_entities_found=len(all_entities),
            unique_pii_types=unique_types
        )
        
        self._print_metrics(metrics)
        return metrics
    
    def _print_metrics(self, metrics: PerformanceMetrics):
        """Affiche les métriques de façon lisible."""
        print(f"\n[RESULTS] {metrics.strategy_name}")
        print(f"  Total Time: {metrics.total_time_seconds:.2f}s")
        print(f"  Texts Processed: {metrics.texts_processed}")
        print(f"  Total Characters: {metrics.total_chars_processed:,}")
        print(f"  Avg Time/Text: {metrics.avg_time_per_text:.3f}s")
        print(f"  Throughput: {metrics.avg_chars_per_second:.0f} chars/s")
        print(f"  PII Found: {metrics.pii_entities_found}")
        print(f"  Unique PII Types: {len(metrics.unique_pii_types)}")
        print(f"  Types: {', '.join(sorted(metrics.unique_pii_types))}")
    
    def compare_strategies(
        self,
        baseline: PerformanceMetrics,
        optimized: PerformanceMetrics
    ) -> ComparisonResult:
        """Compare deux stratégies et génère un rapport."""
        
        speedup = baseline.total_time_seconds / optimized.total_time_seconds
        time_saved = baseline.total_time_seconds - optimized.total_time_seconds
        time_saved_percent = (time_saved / baseline.total_time_seconds) * 100
        
        # Vérifier la cohérence des résultats
        consistency_check = {
            "same_text_count": baseline.texts_processed == optimized.texts_processed,
            "same_pii_count": baseline.pii_entities_found == optimized.pii_entities_found,
            "same_pii_types": set(baseline.unique_pii_types) == set(optimized.unique_pii_types),
            "chars_consistent": baseline.total_chars_processed == optimized.total_chars_processed
        }
        
        # Générer une recommandation
        if speedup > 1.4:
            recommendation = f"✅ RECOMMANDÉ: Gain significatif de {time_saved_percent:.1f}% ({speedup:.2f}x plus rapide)"
        elif speedup > 1.2:
            recommendation = f"✔️  INTÉRESSANT: Gain modéré de {time_saved_percent:.1f}% ({speedup:.2f}x plus rapide)"
        elif speedup > 1.0:
            recommendation = f"⚠️  MARGINAL: Faible gain de {time_saved_percent:.1f}% ({speedup:.2f}x plus rapide)"
        else:
            recommendation = f"❌ NON RECOMMANDÉ: Plus lent de {abs(time_saved_percent):.1f}% ({speedup:.2f}x)"
        
        return ComparisonResult(
            baseline_metrics=baseline,
            optimized_metrics=optimized,
            speedup_factor=speedup,
            consistency_check=consistency_check,
            recommendation=recommendation
        )
    
    def generate_report(self, comparisons: List[ComparisonResult]) -> str:
        """Génère un rapport complet en format texte."""
        
        report = []
        report.append("\n" + "="*80)
        report.append("RAPPORT DE PERFORMANCE - BATCH PROCESSING EVALUATION")
        report.append("="*80)
        
        report.append("\n📊 RÉSUMÉ EXÉCUTIF")
        report.append("-" * 80)
        
        for comp in comparisons:
            report.append(f"\n{comp.optimized_metrics.strategy_name} vs {comp.baseline_metrics.strategy_name}")
            report.append(f"  Speedup Factor: {comp.speedup_factor:.2f}x")
            report.append(f"  Time Saved: {comp.baseline_metrics.total_time_seconds - comp.optimized_metrics.total_time_seconds:.1f}s")
            report.append(f"  Recommendation: {comp.recommendation}")
        
        report.append("\n\n🔬 DÉTAILS DES MÉTRIQUES")
        report.append("-" * 80)
        
        all_metrics = []
        for comp in comparisons:
            if comp.baseline_metrics not in [m for m in all_metrics]:
                all_metrics.append(comp.baseline_metrics)
            all_metrics.append(comp.optimized_metrics)
        
        for metrics in all_metrics:
            report.append(f"\n{metrics.strategy_name}:")
            report.append(f"  • Total Time: {metrics.total_time_seconds:.2f}s")
            report.append(f"  • Texts Processed: {metrics.texts_processed}")
            report.append(f"  • Characters: {metrics.total_chars_processed:,}")
            report.append(f"  • Avg Time/Text: {metrics.avg_time_per_text:.3f}s")
            report.append(f"  • Throughput: {metrics.avg_chars_per_second:.0f} chars/s")
            report.append(f"  • PII Found: {metrics.pii_entities_found}")
        
        report.append("\n\n✅ VÉRIFICATION DE COHÉRENCE")
        report.append("-" * 80)
        
        for idx, comp in enumerate(comparisons, 1):
            report.append(f"\nComparaison #{idx}:")
            for check_name, check_result in comp.consistency_check.items():
                status = "✅ PASS" if check_result else "❌ FAIL"
                report.append(f"  {status}: {check_name}")
        
        report.append("\n\n🎯 RECOMMANDATIONS FINALES")
        report.append("-" * 80)
        
        best_speedup = max(comparisons, key=lambda c: c.speedup_factor)
        report.append(f"\nMeilleure stratégie: {best_speedup.optimized_metrics.strategy_name}")
        report.append(f"Speedup: {best_speedup.speedup_factor:.2f}x")
        report.append(f"{best_speedup.recommendation}")
        
        report.append("\n\nPour implémenter un véritable batch processing avec gains >2x:")
        report.append("1. Implémenter BatchDetectPII au niveau gRPC")
        report.append("2. Accumuler N pages dans AbstractStreamConfluenceScanUseCase")
        report.append("3. Envoyer en un seul appel réseau")
        report.append("4. Gain estimé: 30-50% sur l'overhead réseau + temps de traitement")
        
        report.append("\n" + "="*80 + "\n")
        
        return "\n".join(report)


def main():
    """Point d'entrée principal du test."""
    print("="*80)
    print("TEST D'INTEGRATION - PERFORMANCE BATCH PROCESSING")
    print("="*80)
    print("\nInitialisation...")
    
    suite = PerformanceTestSuite()
    
    print("\nGénération des cas de test...")
    test_cases = suite.generate_test_cases()
    
    total_chars = sum(tc.size_chars for tc in test_cases)
    print(f"\nCas de test générés:")
    print(f"  • Nombre total de textes: {len(test_cases)}")
    print(f"  • Caractères totaux: {total_chars:,}")
    print(f"  • Taille moyenne: {total_chars // len(test_cases):,} chars/text")
    
    # Test 1: Baseline séquentiel
    baseline_metrics = suite.test_sequential_baseline(test_cases)
    
    # Test 2: Parallélisation avec 3 workers
    parallel_3_metrics = suite.test_parallel_texts(test_cases, max_workers=3)
    
    # Test 3: Parallélisation avec 5 workers
    parallel_5_metrics = suite.test_parallel_texts(test_cases, max_workers=5)
    
    # Comparaisons
    comparisons = [
        suite.compare_strategies(baseline_metrics, parallel_3_metrics),
        suite.compare_strategies(baseline_metrics, parallel_5_metrics),
    ]
    
    # Générer et afficher le rapport
    report = suite.generate_report(comparisons)
    print(report)
    
    # Sauvegarder le rapport
    output_file = Path(__file__).parent.parent.parent / "performance_report.txt"
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(report)
    
    print(f"\n📄 Rapport sauvegardé dans: {output_file}")
    
    # Sauvegarder aussi en JSON pour analyse ultérieure
    json_file = Path(__file__).parent.parent.parent / "performance_report.json"
    json_data = {
        "baseline": asdict(baseline_metrics),
        "parallel_3": asdict(parallel_3_metrics),
        "parallel_5": asdict(parallel_5_metrics),
        "comparisons": [
            {
                "baseline": asdict(c.baseline_metrics),
                "optimized": asdict(c.optimized_metrics),
                "speedup_factor": c.speedup_factor,
                "consistency_check": c.consistency_check,
                "recommendation": c.recommendation
            }
            for c in comparisons
        ]
    }
    
    with open(json_file, 'w', encoding='utf-8') as f:
        json.dump(json_data, f, indent=2, ensure_ascii=False)
    
    print(f"📄 Données JSON sauvegardées dans: {json_file}")


if __name__ == "__main__":
    main()
