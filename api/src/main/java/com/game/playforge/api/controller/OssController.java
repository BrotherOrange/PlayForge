package com.game.playforge.api.controller;

import com.game.playforge.api.dto.response.OssPolicyResponse;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.infrastructure.external.oss.OssService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OSS上传签名控制器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    /**
     * 获取OSS上传签名策略
     *
     * @return 签名策略
     */
    @GetMapping("/policy")
    public ApiResult<OssPolicyResponse> getPolicy(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        String directory = "avatars/" + userId;
        log.info("获取OSS上传签名, userId={}, directory={}", userId, directory);
        OssService.PolicyResult result = ossService.generatePostPolicy(directory);
        OssPolicyResponse response = new OssPolicyResponse(
                result.host(), result.policy(), result.signature(),
                result.accessKeyId(), result.key(), result.expire());
        return ApiResult.success(response);
    }
}
