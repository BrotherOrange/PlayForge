package com.game.playforge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.playforge.domain.model.AgentThread;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent会话MyBatis Mapper接口
 * <p>
 * 继承 {@link BaseMapper}，由MyBatis Plus自动提供CRUD实现。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper
public interface AgentThreadMapper extends BaseMapper<AgentThread> {

    @Select("""
            <script>
            SELECT agent_id AS agentId, MAX(id) AS threadId
            FROM t_agent_thread
            WHERE user_id = #{userId}
              AND status != 'DELETED'
              AND agent_id IN
              <foreach item="id" collection="agentIds" open="(" separator="," close=")">
                #{id}
              </foreach>
            GROUP BY agent_id
            </script>
            """)
    List<Map<String, Object>> selectLatestThreadIdsByAgentIds(
            @Param("userId") Long userId,
            @Param("agentIds") List<Long> agentIds);
}
