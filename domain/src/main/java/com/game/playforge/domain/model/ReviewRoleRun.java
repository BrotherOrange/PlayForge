package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_role_run")
public class ReviewRoleRun {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long documentId;

    private String roleKey;

    private String roleName;

    private String roleColor;

    private String status;

    private Integer annotationCount;

    private String errorMessage;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
