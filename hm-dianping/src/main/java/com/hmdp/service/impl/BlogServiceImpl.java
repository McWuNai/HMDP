package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotLog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            isBlogLiked(blog);
            queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog byId = getById(id);
        if (byId == null) return Result.fail("此笔记不存在");
        queryBlogUser(byId);
        isBlogLiked(byId);
        return Result.ok(byId);
    }

    private void isBlogLiked(Blog byId) {
        UserDTO userId = UserHolder.getUser();
        if (userId == null) return;
        // 利用判断是否点赞过来设置isLike参数
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + byId.getId(), userId.getId().toString());
        byId.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 修改点赞数量
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if (score != null) {
            boolean extracted = extracted(id, "-");
            if (extracted) {
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        } else {
            boolean extracted = extracted(id, "+");
            if (extracted) {
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询点赞前五的用户
        Set<String> range = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 1, 4);
        //判断用户是否有值，无则返回空(代表未登录)
        if (range == null || range.isEmpty()) return Result.ok();
        //将从redis获取到的用户解析成 流->map(收集Long值的id)->值(转集合)
        List<Long> collect = range.stream().map(Long::valueOf).collect(Collectors.toList());
        //将id进行细化切割，并转为String类型
        String join = StrUtil.join(",", collect);
        //将获取到的id拿到数据库进行查询,
        // 用 in() 自定义排序方法,
        // last() 拼接末尾条件来进行手动降序，
        // 降完后用list()存为集合，
        // 再用map()转存为hash,
        // 最后调用BeanUtil工具类把User类Copy转存为UserDTO类，
        // 收集流并返回集合
        List<UserDTO> collect1 = userService
                .query()
                .in("id", collect)
                .last("ORDER BY " + join)
                .list()
                .stream()
                .map(
                        user -> BeanUtil.copyProperties(
                                user, UserDTO.class
                        )
                )
                .collect(Collectors.toList());
        return Result.ok(collect1);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if (!save) return Result.fail("笔记发布失败");
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : followUserId) {
            stringRedisTemplate.opsForZSet().
                    add(FEED_KEY + follow.getUserId(),
                            blog.getId().toString(),
                            System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result getFollow(Long lastId, Integer offset) {
        //获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        //设置键名
        String key = FEED_KEY + userId;
        //查找交集
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, offset, 2);
        //判断是否为空
        if (typedTuples == null || typedTuples.isEmpty()) return Result.ok();
        //新建一个list，用于保存 typedTuple.getValue()
        List<Long> ids = new ArrayList<>(typedTuples.size());
        //新建变量，用于覆盖存储分数 typedTuple.getScore() 最新值
        long min = 0;
        int count = 1;
        //根据拿到的交集list获取详细信息
        for (ZSetOperations.TypedTuple<String> typedTuple: typedTuples
             ){
            //储存ID值
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取当前该id的时间戳
            long score = typedTuple.getScore().longValue();
            /*利用遍历储存值来判断是否相等
             相等则表示重复
             重复加1
             不重复则覆盖值
             重置计数器*/
            if (score == min) {
                count++;
            } else {
                min = score;
                count = 1;
            }
        }
        //将获取到的id分割且转为字符串
        String idsStr = StrUtil.join(",", ids);
        //根据id查找blog内容
        List<Blog> blogContext = query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
        for (Blog blogs: blogContext
             ) {
            queryBlogUser(blogs);
            isBlogLiked(blogs);
        }
        //封装结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogContext);
        scrollResult.setMinTime(min);
        scrollResult.setOffset(count);
        //返回结果
        return Result.ok(scrollResult);
    }

    private boolean extracted(Long id, String count) {
        return update()
                .setSql("liked = liked " + count + " 1").eq("id", id)
                .update();
    }

    private void queryBlogUser(Blog byId) {
        Long userId = byId.getUserId();
        User user = userService.getById(userId);
        byId.setName(user.getNickName());
        byId.setIcon(user.getIcon());
    }
}
