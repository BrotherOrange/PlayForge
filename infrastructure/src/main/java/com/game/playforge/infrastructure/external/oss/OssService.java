package com.game.playforge.infrastructure.external.oss;

import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generates direct-upload OSS policies and signed read URLs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssService {

    private static final long DEFAULT_MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final long POLICY_EXPIRE_SECONDS = 300;
    private static final long SIGNED_URL_EXPIRE_SECONDS = 3600;
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final Pattern DIRECTORY_PATTERN = Pattern.compile("^[a-zA-Z0-9/_-]{1,120}$");

    private final OssProperties ossProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public PolicyResult generatePostPolicy(String directory) {
        return generatePostPolicy(directory, DEFAULT_MAX_UPLOAD_BYTES);
    }

    public PolicyResult generatePostPolicy(String directory, long maxUploadBytes) {
        try {
            String safeDirectory = validateDirectory(directory);
            String host = "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint();
            Instant expireTime = Instant.now().plusSeconds(POLICY_EXPIRE_SECONDS);
            String expiration = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(expireTime);

            String key = safeDirectory + "/" + UUID.randomUUID().toString().replace("-", "");
            String policyJson = "{\"expiration\":\"" + expiration + "\","
                    + "\"conditions\":["
                    + "[\"content-length-range\",0," + normalizeMaxUploadBytes(maxUploadBytes) + "],"
                    + "[\"starts-with\",\"$key\",\"" + safeDirectory + "/\"]"
                    + "]}";

            String encodedPolicy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
            String signature = hmacSha1Sign(encodedPolicy);

            log.debug("Generated OSS upload policy, directory={}, key={}, maxBytes={}",
                    safeDirectory, key, normalizeMaxUploadBytes(maxUploadBytes));
            return new PolicyResult(
                    host,
                    encodedPolicy,
                    signature,
                    ossProperties.getAccessKeyId(),
                    key,
                    expireTime.getEpochSecond()
            );
        } catch (Exception e) {
            log.error("Failed to generate OSS upload policy", e);
            throw new BusinessException(ResultCode.OSS_POLICY_ERROR);
        }
    }

    public byte[] downloadObjectBytes(String objectKey) {
        String signedUrl = generateSignedUrl(objectKey);
        if (signedUrl == null || signedUrl.isBlank()) {
            throw new BusinessException(ResultCode.OSS_OBJECT_FETCH_FAILED);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Failed to fetch OSS object, objectKey={}, status={}", objectKey, response.statusCode());
                throw new BusinessException(ResultCode.OSS_OBJECT_FETCH_FAILED);
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while fetching OSS object, objectKey={}", objectKey, e);
            throw new BusinessException(ResultCode.OSS_OBJECT_FETCH_FAILED);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to fetch OSS object, objectKey={}", objectKey, e);
            throw new BusinessException(ResultCode.OSS_OBJECT_FETCH_FAILED);
        }
    }

    public String generateSignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("/") || objectKey.contains("..")) {
            log.warn("Illegal OSS object key, objectKey={}", objectKey);
            return null;
        }
        try {
            long expires = Instant.now().plusSeconds(SIGNED_URL_EXPIRE_SECONDS).getEpochSecond();
            String canonicalResource = "/" + ossProperties.getBucketName() + "/" + objectKey;
            String stringToSign = "GET\n\n\n" + expires + "\n" + canonicalResource;
            String signature = hmacSha1Sign(stringToSign);
            String encodedSignature = URLEncoder.encode(signature, StandardCharsets.UTF_8);

            return "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint()
                    + "/" + objectKey
                    + "?OSSAccessKeyId=" + ossProperties.getAccessKeyId()
                    + "&Expires=" + expires
                    + "&Signature=" + encodedSignature;
        } catch (Exception e) {
            log.warn("Failed to generate signed OSS URL, objectKey={}", objectKey, e);
            return null;
        }
    }

    private String hmacSha1Sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA1);
        mac.init(new SecretKeySpec(ossProperties.getAccessKeySecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String validateDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Directory must not be blank.");
        }
        String normalized = directory.trim();
        if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("Directory format is invalid.");
        }
        if (!DIRECTORY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Directory format is invalid.");
        }
        return normalized;
    }

    private long normalizeMaxUploadBytes(long maxUploadBytes) {
        return maxUploadBytes > 0 ? maxUploadBytes : DEFAULT_MAX_UPLOAD_BYTES;
    }

    public record PolicyResult(
            String host,
            String policy,
            String signature,
            String accessKeyId,
            String key,
            long expire
    ) {
    }
}
