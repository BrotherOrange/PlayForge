package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_annotation")
public class ReviewAnnotation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long documentId;

    private Long roleRunId;

    private String roleKey;

    private String roleName;

    private String roleColor;

    private String annotationType;

    private String priority;

    private String blockId;

    private Integer startOffset;

    private Integer endOffset;

    private String quoteText;

    private String title;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
