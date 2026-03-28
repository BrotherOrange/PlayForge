package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_document")
public class ReviewDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private String sourceType;

    private Integer sortOrder;

    private String title;

    private String originalFilename;

    private String markdownContent;

    private String blocksJson;

    private String status;

    private String warningsJson;

    private String summaryMarkdown;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
