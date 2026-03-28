package com.game.playforge.common.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Unified business result codes.
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    SUCCESS(0, "OK", 200),

    // 10xx - Authentication
    CREDENTIALS_ERROR(1001, "Invalid phone number or password", 401),
    PHONE_ALREADY_REGISTERED(1002, "Phone number already registered", 409),
    TOKEN_EXPIRED(1003, "Access token expired", 401),
    TOKEN_INVALID(1004, "Access token is invalid", 401),
    REFRESH_TOKEN_INVALID(1005, "Refresh token is invalid", 401),
    NOT_LOGGED_IN(1006, "Not logged in", 401),

    // 20xx - User
    USER_NOT_FOUND(2001, "User not found", 404),

    // 30xx - OSS
    OSS_POLICY_ERROR(3001, "Failed to generate OSS upload policy", 500),
    OSS_OBJECT_FETCH_FAILED(3002, "Failed to fetch uploaded file from OSS", 502),

    // 40xx - Generic client errors
    NOT_FOUND(4001, "Resource not found", 404),
    ADMIN_REQUIRED(4002, "Administrator permission is required", 403),
    METHOD_NOT_ALLOWED(4003, "Request method is not supported", 405),

    // 50xx - Agent
    AGENT_NOT_FOUND(5001, "Agent not found", 404),
    THREAD_NOT_FOUND(5002, "Thread not found", 404),
    THREAD_ACCESS_DENIED(5003, "Thread access denied", 403),
    AGENT_PROVIDER_UNAVAILABLE(5004, "AI provider is unavailable", 503),
    AGENT_TOOL_ERROR(5005, "Tool execution failed", 500),
    AGENT_ACCESS_DENIED(5006, "Agent access denied", 403),

    // 60xx - Document review
    DOCUMENT_CONVERSION_FAILED(6001, "Document conversion failed", 502),
    DOCUMENT_FORMAT_UNSUPPORTED(6002, "Unsupported document format", 400),
    REVIEW_TASK_NOT_FOUND(6003, "Review task not found", 404),
    REVIEW_TASK_LIMIT_EXCEEDED(6004, "At most three documents can be reviewed at once", 400),
    REVIEW_MODEL_UNSUPPORTED(6005, "Unsupported review model", 400),
    REVIEW_INPUT_EMPTY(6006, "At least one document or text entry is required", 400),

    // 9xxx - System
    INTERNAL_ERROR(9001, "Internal server error", 500),
    PARAM_VALIDATION_FAILED(9002, "Parameter validation failed", 400);

    private final int code;
    private final String message;
    private final int httpStatus;
}
