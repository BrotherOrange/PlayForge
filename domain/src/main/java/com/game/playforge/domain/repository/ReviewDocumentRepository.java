package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.ReviewDocument;

import java.util.List;

public interface ReviewDocumentRepository {

    ReviewDocument findById(Long id);

    List<ReviewDocument> findByTaskId(Long taskId);

    void insert(ReviewDocument reviewDocument);

    void update(ReviewDocument reviewDocument);
}
