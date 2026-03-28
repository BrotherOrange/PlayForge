package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.ReviewRoleRun;
import com.game.playforge.domain.repository.ReviewRoleRunRepository;
import com.game.playforge.infrastructure.persistence.mapper.ReviewRoleRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewRoleRunRepositoryImpl implements ReviewRoleRunRepository {

    private final ReviewRoleRunMapper reviewRoleRunMapper;

    @Override
    public ReviewRoleRun findById(Long id) {
        return reviewRoleRunMapper.selectById(id);
    }

    @Override
    public List<ReviewRoleRun> findByDocumentId(Long documentId) {
        return reviewRoleRunMapper.selectList(new LambdaQueryWrapper<ReviewRoleRun>()
                .eq(ReviewRoleRun::getDocumentId, documentId)
                .orderByAsc(ReviewRoleRun::getCreatedAt));
    }

    @Override
    public List<ReviewRoleRun> findByDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return reviewRoleRunMapper.selectList(new LambdaQueryWrapper<ReviewRoleRun>()
                .in(ReviewRoleRun::getDocumentId, documentIds)
                .orderByAsc(ReviewRoleRun::getCreatedAt));
    }

    @Override
    public void insert(ReviewRoleRun reviewRoleRun) {
        reviewRoleRunMapper.insert(reviewRoleRun);
    }

    @Override
    public void update(ReviewRoleRun reviewRoleRun) {
        reviewRoleRunMapper.updateById(reviewRoleRun);
    }
}
