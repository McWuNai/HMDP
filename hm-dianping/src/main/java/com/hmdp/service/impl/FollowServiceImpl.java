package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService userService;

    @Override
    @Transactional

    public Result isFollow(Long userId, Boolean isFollow) {
        Long id = UserHolder.getUser().getId();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(id).setFollowUserId(userId);
            stringRedisTemplate.opsForSet().add("blog:follow:hmdpon:" + id, userId.toString());
            save(follow);
        } else {
            stringRedisTemplate.opsForSet().remove("blog:follow:hmdpon:" + id, userId.toString());
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", id)
                    .eq("follow_user_id", userId));
        }
        return Result.ok();
    }

    @Override
    public Result follow(Long followId) {
        Long id = UserHolder.getUser().getId();
        Integer count = query()
                .eq("user_id", id)
                .eq("follow_user_id", followId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result hmdponFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet()
                .intersect("blog:follow:hmdpon:" + id
                        , "blog:follow:hmdpon:" + userId
                );
        if (intersect == null || intersect.isEmpty()) return Result.ok(Collections.emptyList());
        QueryChainWrapper<User> id1 = userService.query().in("id", intersect);
        List<User> list = id1.list();
        return Result.ok(list);
    }
}
