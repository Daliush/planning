package com.planning.repository.jpa;

import com.planning.repository.entity.Entities.AssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentJpaRepository extends JpaRepository<AssignmentEntity, UUID> {

    /**
     * Il n'existe qu'un seul emploi du temps : « celui de la 6e A », « celui de
     * Jean » et « l'occupation de la salle B12 » sont trois filtres sur cette même
     * liste plate (§6.14).
     */
    List<AssignmentEntity> findBySolveJobIdOrderByDayInCycleAscStartTimeAsc(UUID solveJobId);

    void deleteBySolveJobId(UUID solveJobId);
}
