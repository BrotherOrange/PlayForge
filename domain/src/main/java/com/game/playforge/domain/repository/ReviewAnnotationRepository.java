package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.ReviewAnnotation;

import java.util.List;

public interface ReviewAnnotationRepository {

    List<ReviewAnnotation> findByDocumentId(Long documentId);

    List<ReviewAnnotation> findByDocumentIds(List<Long> documentIds);

    void insert(ReviewAnnotation reviewAnnotation);
}
