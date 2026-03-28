package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.ReviewReportHistory;

import java.util.List;

public interface ReviewReportHistoryRepository {

    List<ReviewReportHistory> findByTaskId(Long taskId, String reportScope, int limit);

    void insert(ReviewReportHistory reviewReportHistory);
}
