package com.game.playforge.infrastructure.external.oss;

import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * OSS签名服务
 * <p>
 * 生成POST直传签名策略和签名GET URL，前端直接与OSS交互，无需创建OSSClient实例。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssService {

    private static final long POLICY_EXPIRE_SECONDS = 300;
    private static final long SIGNED_URL_EXPIRE_SECONDS = 3600;
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final Pattern DIRECTORY_PATTERN = Pattern.compile("^[a-zA-Z0-9/_-]{1,120}$");

    private final OssProperties ossProperties;

    /**
     * 生成POST直传签名策略
     *
     * @param directory 上传目录（如 avatars）
     * @return 签名策略信息
     */
    public PolicyResult generatePostPolicy(String directory) {
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
                    + "[\"content-length-range\",0,10485760],"
                    + "[\"starts-with\",\"$key\",\"" + safeDirectory + "/\"]"
                    + "]}";

            String encodedPolicy = Base64.getEncoder().encodeToString(
                    policyJson.getBytes(StandardCharsets.UTF_8));
            String signature = hmacSha1Sign(encodedPolicy);

            log.debug("生成OSS上传签名, directory={}, key={}", safeDirectory, key);
            return new PolicyResult(host, encodedPolicy, signature,
                    ossProperties.getAccessKeyId(), key, expireTime.getEpochSecond());
        } catch (Exception e) {
            log.error("生成OSS上传签名失败", e);
            throw new BusinessException(ResultCode.OSS_POLICY_ERROR);
        }
    }

    /**
     * 生成签名GET URL（用于私有Bucket的对象读取）
     *
     * @param objectKey 对象Key（如 avatars/xxx.jpeg）
     * @return 带签名的完整URL，有效期1小时
     */
    public String generateSignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("/") || objectKey.contains("..")) {
            log.warn("对象Key非法, objectKey={}", objectKey);
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
            log.warn("生成签名URL失败, objectKey={}", objectKey, e);
            return null;
        }
    }

    private String hmacSha1Sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA1);
        mac.init(new SecretKeySpec(
                ossProperties.getAccessKeySecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String validateDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("directory不能为空");
        }
        String normalized = directory.trim();
        if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("directory不合法");
        }
        if (!DIRECTORY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("directory不合法");
        }
        return normalized;
    }

    /**
     * 签名策略结果
     */
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
