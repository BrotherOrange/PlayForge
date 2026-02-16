package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.User;
import com.game.playforge.domain.repository.UserRepository;
import com.game.playforge.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 用户仓储实现
 * <p>
 * 基于MyBatis Plus的 {@link UserMapper} 实现持久化操作。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public void insert(User user) {
        log.info("新增用户, phone={}", user.getPhone());
        userMapper.insert(user);
        log.info("新增用户成功, userId={}", user.getId());
    }

    @Override
    public User findById(Long id) {
        log.debug("根据ID查询用户, userId={}", id);
        User user = userMapper.selectById(id);
        log.debug("根据ID查询用户结果, userId={}, found={}", id, user != null);
        return user;
    }

    @Override
    public User findByPhone(String phone) {
        log.debug("根据手机号查询用户, phone={}", phone);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        log.debug("根据手机号查询用户结果, phone={}, found={}", phone, user != null);
        return user;
    }

    @Override
    public void update(User user) {
        log.info("更新用户信息, userId={}", user.getId());
        userMapper.updateById(user);
        log.info("更新用户信息成功, userId={}", user.getId());
    }
}
