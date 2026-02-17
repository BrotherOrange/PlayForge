package com.game.playforge.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 会话ID
     */
    private Long threadId;

    /**
     * 回复内容
     */
    private String content;
}
