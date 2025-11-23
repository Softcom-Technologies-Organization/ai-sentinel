package pro.softcom.sentinelle.infrastructure.pii.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.sentinelle.infrastructure.pii.scan.service.ScanEventBuffer;
import pro.softcom.sentinelle.infrastructure.pii.scan.service.ScanEventSequencer;

/**
 * Configuration Spring pour les services de gestion des événements de scan.
 * <p>
 * Cette configuration instancie manuellement les services de l'application layer
 * qui ne peuvent pas utiliser d'annotations Spring (@Service) pour respecter
 * le principe d'indépendance framework de l'architecture hexagonale.
 */
@Configuration
public class ScanEventConfig {

    /**
     * Crée le bean ScanEventSequencer pour la génération d'IDs séquentiels.
     *
     * @return instance de ScanEventSequencer
     */
    @Bean
    public ScanEventSequencer scanEventSequencer() {
        return new ScanEventSequencer();
    }

    /**
     * Crée le bean ScanEventBuffer pour le buffering des événements SSE.
     *
     * @return instance de ScanEventBuffer
     */
    @Bean
    public ScanEventBuffer scanEventBuffer() {
        return new ScanEventBuffer();
    }
}
