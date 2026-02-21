package com.game.playforge.infrastructure.external.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具
 * <p>
 * 提供当前日期和时间查询能力，供Agent调用。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component("dateTimeTool")
public class DateTimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Tool("获取当前日期和时间")
    public String getCurrentDateTime() {
        String dateTime = ZonedDateTime.now(ZONE).format(FORMATTER);
        log.debug("获取当前日期时间: {}", dateTime);
        return dateTime;
    }
}
