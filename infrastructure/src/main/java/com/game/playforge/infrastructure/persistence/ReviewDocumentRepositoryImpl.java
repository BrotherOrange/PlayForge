package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.ReviewDocument;
import com.game.playforge.domain.repository.ReviewDocumentRepository;
import com.game.playforge.infrastructure.persistence.mapper.ReviewDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewDocumentRepositoryImpl implements ReviewDocumentRepository {

    private final ReviewDocumentMapper reviewDocumentMapper;

    @Override
    public ReviewDocument findById(Long id) {
        return reviewDocumentMapper.selectById(id);
    }

    @Override
    public List<ReviewDocument> findByTaskId(Long taskId) {
        return reviewDocumentMapper.selectList(new LambdaQueryWrapper<ReviewDocument>()
                .eq(ReviewDocument::getTaskId, taskId)
                .orderByAsc(ReviewDocument::getSortOrder)
                .orderByAsc(ReviewDocument::getCreatedAt));
    }

    @Override
    public void insert(ReviewDocument reviewDocument) {
        reviewDocumentMapper.insert(reviewDocument);
    }

    @Override
    public void update(ReviewDocument reviewDocument) {
        reviewDocumentMapper.updateById(reviewDocument);
    }
}
