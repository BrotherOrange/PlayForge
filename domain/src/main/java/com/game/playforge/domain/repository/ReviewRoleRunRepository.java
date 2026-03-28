package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.ReviewRoleRun;

import java.util.List;

public interface ReviewRoleRunRepository {

    ReviewRoleRun findById(Long id);

    List<ReviewRoleRun> findByDocumentId(Long documentId);

    List<ReviewRoleRun> findByDocumentIds(List<Long> documentIds);

    void insert(ReviewRoleRun reviewRoleRun);

    void update(ReviewRoleRun reviewRoleRun);
}
