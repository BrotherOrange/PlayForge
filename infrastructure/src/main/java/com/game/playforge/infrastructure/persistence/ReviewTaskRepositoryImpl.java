package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.ReviewTask;
import com.game.playforge.domain.repository.ReviewTaskRepository;
import com.game.playforge.infrastructure.persistence.mapper.ReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewTaskRepositoryImpl implements ReviewTaskRepository {

    private final ReviewTaskMapper reviewTaskMapper;

    @Override
    public ReviewTask findById(Long id) {
        return reviewTaskMapper.selectById(id);
    }

    @Override
    public ReviewTask findByPublicId(String publicId) {
        return reviewTaskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getPublicId, publicId));
    }

    @Override
    public List<ReviewTask> findByStatuses(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Collections.emptyList();
        }
        return reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                .in(ReviewTask::getStatus, statuses));
    }

    @Override
    public List<ReviewTask> findRecentByUserId(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }
        return reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getCreatedByUserId, userId)
                .orderByDesc(ReviewTask::getUpdatedAt, ReviewTask::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Override
    public void insert(ReviewTask reviewTask) {
        reviewTaskMapper.insert(reviewTask);
    }

    @Override
    public void update(ReviewTask reviewTask) {
        reviewTaskMapper.updateById(reviewTask);
    }
}
