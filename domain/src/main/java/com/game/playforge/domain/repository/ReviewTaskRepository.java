package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.ReviewTask;

import java.util.Collection;
import java.util.List;

public interface ReviewTaskRepository {

    ReviewTask findById(Long id);

    ReviewTask findByPublicId(String publicId);

    List<ReviewTask> findByStatuses(Collection<String> statuses);

    List<ReviewTask> findRecentByUserId(Long userId, int limit);

    void insert(ReviewTask reviewTask);

    void update(ReviewTask reviewTask);
}
