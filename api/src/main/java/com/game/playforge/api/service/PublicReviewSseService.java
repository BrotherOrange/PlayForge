package com.game.playforge.api.service;

import com.game.playforge.application.dto.review.PublicReviewTaskDetail;
import com.game.playforge.application.service.review.PublicReviewStreamPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class PublicReviewSseService implements PublicReviewStreamPublisher {

    private static final long EMITTER_TIMEOUT_MILLIS = 1_800_000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByPublicId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String publicId, PublicReviewTaskDetail initialSnapshot) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        emittersByPublicId.computeIfAbsent(publicId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(publicId, emitter));
        emitter.onTimeout(() -> removeEmitter(publicId, emitter));
        emitter.onError(error -> removeEmitter(publicId, emitter));

        sendSnapshot(emitter, initialSnapshot);
        return emitter;
    }

    @Override
    public void publishSnapshot(String publicId, PublicReviewTaskDetail snapshot) {
        List<SseEmitter> emitters = emittersByPublicId.get(publicId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                sendSnapshot(emitter, snapshot);
            } catch (Exception e) {
                log.warn("Failed to publish public review snapshot, publicId={}", publicId, e);
                removeEmitter(publicId, emitter);
            }
        }
    }

    private void sendSnapshot(SseEmitter emitter, PublicReviewTaskDetail snapshot) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send SSE snapshot.", e);
        }
    }

    private void removeEmitter(String publicId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByPublicId.get(publicId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByPublicId.remove(publicId);
        }
    }
}
