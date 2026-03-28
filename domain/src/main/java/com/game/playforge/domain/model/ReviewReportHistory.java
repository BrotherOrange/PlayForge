package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_report_history")
public class ReviewReportHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long documentId;

    private String reportScope;

    private String title;

    private String provider;

    private String modelName;

    private String reportMarkdown;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
