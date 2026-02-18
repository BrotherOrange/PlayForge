package com.game.playforge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.playforge.domain.model.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent消息MyBatis Mapper接口
 * <p>
 * 继承 {@link BaseMapper}，由MyBatis Plus自动提供CRUD实现。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    @Select("""
            SELECT id, thread_id, role, content, tool_name, token_count, created_at
            FROM t_agent_message
            WHERE thread_id = #{threadId}
            ORDER BY created_at ASC, id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AgentMessage> selectByThreadIdPaged(
            @Param("threadId") Long threadId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT id, thread_id, role, content, tool_name, token_count, created_at
            FROM t_agent_message
            WHERE thread_id = #{threadId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<AgentMessage> selectLatestByThreadId(
            @Param("threadId") Long threadId,
            @Param("limit") int limit);
}
