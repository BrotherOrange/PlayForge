package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.ReviewReportHistory;
import com.game.playforge.domain.repository.ReviewReportHistoryRepository;
import com.game.playforge.infrastructure.persistence.mapper.ReviewReportHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewReportHistoryRepositoryImpl implements ReviewReportHistoryRepository {

    private final ReviewReportHistoryMapper reviewReportHistoryMapper;

    @Override
    public List<ReviewReportHistory> findByTaskId(Long taskId, String reportScope, int limit) {
        int safeLimit = Math.max(1, limit);
        return reviewReportHistoryMapper.selectList(new LambdaQueryWrapper<ReviewReportHistory>()
                .eq(ReviewReportHistory::getTaskId, taskId)
                .eq(ReviewReportHistory::getReportScope, reportScope)
                .orderByDesc(ReviewReportHistory::getCreatedAt)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public void insert(ReviewReportHistory reviewReportHistory) {
        reviewReportHistoryMapper.insert(reviewReportHistory);
    }
}
