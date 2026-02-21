package com.game.playforge.common.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类为技能定义
 * <p>
 * 被标注的类将被 SkillRegistry 自动扫描和注册。
 * 技能内容按需加载，system prompt 中只注入轻量级目录。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Skill {

    /**
     * 技能唯一标识，默认使用 Bean 名称
     */
    String name() default "";

    /**
     * 简要描述（出现在技能目录中，帮助 LLM 判断何时使用）
     */
    String description() default "";

    /**
     * 完整内容文件引用（classpath:prompts/ 下的文件名，按需加载时返回）
     */
    String contentRef() default "";

    /**
     * 内联完整内容（短文本时使用，与 contentRef 二选一）
     */
    String content() default "";

    /**
     * 该技能附带的外部 Tool Bean 名称
     */
    String[] toolNames() default {};
}
