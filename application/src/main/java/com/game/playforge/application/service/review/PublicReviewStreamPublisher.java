package com.game.playforge.application.service.review;

import com.game.playforge.application.dto.review.PublicReviewTaskDetail;

public interface PublicReviewStreamPublisher {

    void publishSnapshot(String publicId, PublicReviewTaskDetail snapshot);
}
