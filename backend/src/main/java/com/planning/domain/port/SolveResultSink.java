package com.planning.domain.port;

import com.planning.domain.model.SolveReport;
import com.planning.domain.port.Rows.AssignmentRow;

import java.util.List;
import java.util.UUID;

/** Où le worker écrit ce qu'il a produit. Implémenté par la couche persistance. */
public interface SolveResultSink {

    void started(UUID jobId);

    void succeeded(UUID jobId, int hardScore, int softScore,
                   List<AssignmentRow> assignments, SolveReport report);

    void failed(UUID jobId, String errorCode, String errorDetail);
}
