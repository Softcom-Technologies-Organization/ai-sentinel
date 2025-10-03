package pro.softcom.sentinelle.application.pii.reporting.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanCheckpointService;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanProgressCalculator;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorSettings;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application use case orchestrating Confluence scans and PII detection.
 * What: encapsulates business/reactive flow away from the web controller.
 * Returns ScanEvent stream that the presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceScanUseCaseImpl extends AbstractStreamConfluenceScanUseCase implements StreamConfluenceScanUseCase {

    public StreamConfluenceScanUseCaseImpl(ConfluenceClient confluenceService,
                                           ConfluenceAttachmentClient confluenceAttachmentService,
                                           PiiDetectorSettings piiSettings,
                                           PiiDetectorClient piiDetectorClient,
                                           ScanEventStore scanEventStore,
                                           ScanEventFactory eventFactory,
                                           ScanProgressCalculator progressCalculator,
                                           ScanCheckpointService checkpointService,
                                           AttachmentProcessor attachmentProcessor) {
        super(confluenceService, confluenceAttachmentService, piiSettings, piiDetectorClient,
              scanEventStore, eventFactory, progressCalculator, checkpointService, attachmentProcessor);
    }


    /**
     * Streams scan events for a single Confluence space.
     * Pédagogie WebFlux (technique) :
     * — Un identifiant de scan est généré pour corréler tous les évènements du flux.
     * — Mono.fromFuture(...) permet de "ponter" un CompletableFuture (API Confluence) vers le monde réactif.
     * — FlatMapMany(...) transforme le Mono (0..1) en Flux (0..N) selon le résultat obtenu.
     * — Si l'espace n'existe pas, on renvoie immédiatement un Flux à 1 élément (évènement d'erreur) via Flux.just(...).
     * — Sinon, on charge les pages de l'espace (toujours via fromFuture), puis on délègue au runScanFlux(...)
     *   qui produit un Flux<ScanResult> représentant la séquence d'évènements (start, progrès, résultats, fin...).
     * — onErrorResume capture toute erreur asynchrone survenant dans la chaîne et bascule sur un Flux d'erreur métier lisible.
     *
     * Propriétés réactives utiles :
     * — Paresse (laziness) : rien n'est exécuté tant qu'il n'y a pas d'abonné côté contrôleur (SSE par ex.).
     * — Backpressure : Flux émet au rythme demandé par l'abonné ; ici, la concaténation et les opérateurs utilisés sont
     *   safe pour un traitement séquentiel sans surcharge mémoire.
     */
    @Override
    public Flux<ScanResult> streamSpace(String spaceKey) {
        // Identifiant unique pour tracer et regrouper tous les évènements d'un même scan
        String scanId = UUID.randomUUID().toString();

        // Passage du monde Future → réactif. La requête n'est pas exécutée tant qu'il n'y a pas d'abonné.
        return Mono.fromFuture(confluenceClient.getSpace(spaceKey))
            // On transforme le Mono<Optional<ConfluenceSpace>> en Flux<ScanResult>
            .flatMapMany(confluenceSpaceOpt -> {
                // Cas 1 : espace introuvable → on retourne un petit Flux d'un seul évènement d'erreur
                if (confluenceSpaceOpt.isEmpty()) {
                    return Flux.just(ScanResult.builder()
                                         .scanId(scanId)
                                         .spaceKey(spaceKey)
                                         .eventType(DetectionReportingEventType.ERROR.getLabel())
                                         .message("Espace non trouvé")
                                         .emittedAt(Instant.now().toString())
                                         .build());
                }
                // Cas 2 : espace trouvé → on récupère toutes ses pages puis on lance le flux de scan
                return Mono.fromFuture(confluenceClient.getAllPagesInSpace(spaceKey))
                    // runScanFlux(...) retourne déjà un Flux<ScanResult> représentant la progression complète
                    .flatMapMany(pages -> runScanFlux(scanId, spaceKey, pages, 0, pages.size()));
            })
            // Filet de sécurité global : transforme toute exception en évènement d'erreur consommable côté UI
            .onErrorResume(exception -> {
                log.error("[USECASE] Erreur dans le flux: {}", exception.getMessage(), exception);
                return Flux.just(ScanResult.builder()
                                     .scanId(scanId)
                                     .spaceKey(spaceKey)
                                     .eventType(DetectionReportingEventType.ERROR.getLabel())
                                     .message(exception.getMessage())
                                     .emittedAt(Instant.now().toString())
                                     .build());
            });
    }

    /**
     * Streams scan events for all spaces sequentially.
     *
     * Pédagogie WebFlux (technique) :
     * - On découpe le flux global en trois segments : en-tête (MULTI_START), corps (traitement espaces), pied (MULTI_COMPLETE).
     * - Flux.concat(header, body, footer) garantit l'exécution stricte et séquentielle des segments dans cet ordre.
     * - Chaque segment est un Flux paresseux, rien n'est lancé tant qu'il n'y a pas d'abonné.
     */
    @Override
    public Flux<ScanResult> streamAllSpaces() {
        String scanCorrelationId = UUID.randomUUID().toString();

        // Segment d'ouverture: un seul évènement "MULTI_START"
        Flux<ScanResult> header = buildAllSpaceScanFluxHeader(scanCorrelationId);

        // Segment principal: itère sur les espaces et effectue les scans de façon séquentielle
        Flux<ScanResult> body = buildAllSpaceScanFluxBody(scanCorrelationId);

        // Segment de clôture: un seul évènement "MULTI_COMPLETE"
        Flux<ScanResult> footer = buildAllSpaceScanFluxFooter(scanCorrelationId);

        // Concaténation séquentielle et ordonnée des segments
        return Flux.concat(header, body, footer);
    }

    private static Flux<ScanResult> buildAllSpaceScanFluxFooter(String scanId) {
        return Flux.just(ScanResult.builder()
                             .scanId(scanId)
                             .eventType(DetectionReportingEventType.MULTI_COMPLETE.getLabel())
                             .emittedAt(Instant.now().toString())
                             .build());
    }

    private Flux<ScanResult> buildAllSpaceScanFluxBody(String scanId) {
        // Récupération asynchrone de tous les espaces (Future -> Mono)
        return Mono.fromFuture(confluenceClient.getAllSpaces())
            // On déroule ensuite en Flux<ScanResult>
            .flatMapMany(spaces -> {
                // Si la liste est vide, on génère un petit Flux d'erreur. Sinon, on crée le flux de scan.
                // NB: createErrorScanResultIfNoSpace(...) renvoie null quand tout va bien, ce qui nous permet
                // d'utiliser Objects.requireNonNullElseGet(...) pour basculer vers le flux de traitement.
                Flux<ScanResult> errrorScanResultsFlux = createErrorScanResultIfNoSpace(scanId, spaces);
                return Objects.requireNonNullElseGet(errrorScanResultsFlux, () -> createScanResultFlux(scanId, spaces));
            })
            // Gestion d'erreur globale: toute exception est mappée sur un évènement métier lisible
            .onErrorResume(exception -> {
                log.error("[USECASE] Erreur globale du flux multi-espaces: {}",
                          exception.getMessage(),
                          exception);
                return Flux.just(ScanResult.builder()
                                     .scanId(scanId)
                                     .eventType(DetectionReportingEventType.ERROR.getLabel())
                                     .message(exception.getMessage())
                                     .emittedAt(Instant.now().toString())
                                     .build());
            });
    }

    private Flux<ScanResult> createScanResultFlux(String scanId, List<ConfluenceSpace> spaces) {
        // Flux sur la liste des espaces à traiter
        return Flux.fromIterable(spaces)
            // concatMap => traitement séquentiel (important pour garder un ordre prévisible et limiter la pression mémoire).
            // A la différence de flatMap, concatMap attend la fin du flux précédent avant de passer au suivant.
            .concatMap(
                space -> Mono.fromFuture(
                        confluenceClient.getAllPagesInSpace(space.key()))
                    // On lance ensuite le flux de scan pour cet espace
                    .flatMapMany(
                        pages -> runScanFlux(scanId,
                                             space.key(),
                                             pages, 0,
                                             pages.size()))
                    // Filet d'erreur local à un espace: on émet un évènement d'erreur mais on continue les autres espaces
                    .onErrorResume(exception -> {
                        log.error(
                            "[USECASE] Erreur lors du scan de l'espace {}: {}",
                            space.key(),
                            exception.getMessage(),
                            exception);
                        return Flux.just(
                            ScanResult.builder()
                                .scanId(scanId)
                                .spaceKey(space.key())
                                .eventType(DetectionReportingEventType.ERROR.getLabel())
                                .message(exception.getMessage())
                                .emittedAt(Instant.now().toString())
                                .build());
                    }));
    }

    private static Flux<ScanResult> createErrorScanResultIfNoSpace(String scanId, List<ConfluenceSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return Flux.just(ScanResult.builder()
                                 .scanId(scanId)
                                 .eventType(DetectionReportingEventType.ERROR.getLabel())
                                 .message("Aucun espace trouvé")
                                 .emittedAt(Instant.now().toString())
                                 .build());
        }
        return null;
    }

    private static Flux<ScanResult> buildAllSpaceScanFluxHeader(String scanId) {
        return Flux.just(ScanResult.builder()
                             .scanId(scanId)
                             .eventType(DetectionReportingEventType.MULTI_START.getLabel())
                             .emittedAt(Instant.now().toString())
                             .build());
    }

}
