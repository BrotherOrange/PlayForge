package com.game.playforge.application.service;

import com.game.playforge.application.dto.review.PublicReviewCreateCommand;
import com.game.playforge.application.dto.review.PublicReviewCreateResult;
import com.game.playforge.application.dto.review.PublicReviewTaskDetail;
import com.game.playforge.application.dto.review.PublicReviewTaskSummary;

import java.util.List;

public interface PublicReviewService {

    PublicReviewCreateResult createReviewTask(PublicReviewCreateCommand command);

    PublicReviewTaskDetail getReviewTask(String publicId);

    PublicReviewTaskDetail generateOverallReport(String publicId);

    List<PublicReviewTaskSummary> listReviewTasks(Long userId, int limit);
}
