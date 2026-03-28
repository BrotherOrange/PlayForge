package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.ReviewAnnotation;
import com.game.playforge.domain.repository.ReviewAnnotationRepository;
import com.game.playforge.infrastructure.persistence.mapper.ReviewAnnotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewAnnotationRepositoryImpl implements ReviewAnnotationRepository {

    private final ReviewAnnotationMapper reviewAnnotationMapper;

    @Override
    public List<ReviewAnnotation> findByDocumentId(Long documentId) {
        return reviewAnnotationMapper.selectList(new LambdaQueryWrapper<ReviewAnnotation>()
                .eq(ReviewAnnotation::getDocumentId, documentId)
                .orderByAsc(ReviewAnnotation::getCreatedAt));
    }

    @Override
    public List<ReviewAnnotation> findByDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return reviewAnnotationMapper.selectList(new LambdaQueryWrapper<ReviewAnnotation>()
                .in(ReviewAnnotation::getDocumentId, documentIds)
                .orderByAsc(ReviewAnnotation::getCreatedAt));
    }

    @Override
    public void insert(ReviewAnnotation reviewAnnotation) {
        reviewAnnotationMapper.insert(reviewAnnotation);
    }
}
