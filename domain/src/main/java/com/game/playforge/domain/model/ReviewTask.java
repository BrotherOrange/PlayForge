package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_task")
public class ReviewTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String publicId;

    private String title;

    private String provider;

    private String modelName;

    private String status;

    private Integer documentCount;

    private Long createdByUserId;

    private String overallReportMarkdown;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
