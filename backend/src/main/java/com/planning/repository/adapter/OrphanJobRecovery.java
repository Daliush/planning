package com.planning.repository.adapter;

import com.planning.repository.entity.Entities.SolveJobEntity;
import com.planning.repository.jpa.SolveJobJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Solde les jobs restés en vol après un arrêt du processus.
 *
 * <p>Les workers sont en mémoire et sans état (ADR-004) : aucun job {@code QUEUED}
 * ou {@code RUNNING} ne survit à un redémarrage. Les laisser en l'état ferait
 * croire à une résolution éternellement en cours, et bloquerait le lancement de la
 * suivante côté interface.
 */
@Component
public class OrphanJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrphanJobRecovery.class);

    private final SolveJobJpaRepository jobs;

    public OrphanJobRecovery(SolveJobJpaRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SolveJobEntity> orphans = jobs.findByStatusIn(List.of("QUEUED", "RUNNING"));
        if (orphans.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        orphans.forEach(job -> {
            job.status = "FAILED";
            job.errorCode = "INTERRUPTED";
            job.errorDetail = "La résolution a été interrompue par un arrêt du service. Relancez-la.";
            job.finishedAt = now;
        });
        jobs.saveAll(orphans);
        log.warn("{} job(s) de résolution interrompu(s) par un arrêt précédent ont été soldé(s)",
                orphans.size());
    }
}
