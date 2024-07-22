package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


//        新建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//        设置redis String的key value
    public void set(String key, Object value, Long time, TimeUnit unit) {
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, unit);
    }
//        设置redis String的key value(逻辑时间)
    public void setWithLogical(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
    }


//        缓存穿透
    public <R, ID> R queryWithPassThrough(String prefix, ID id, Long RandomNumber, Class<R> type, Function<ID, R> dbFallback, TimeUnit unit) {
//        查询商铺缓存
        String cacheId = stringRedisTemplate.opsForValue().get(prefix + id);
//        判断是否存在
        if (Objects.equals(cacheId, "")) {
            return null;
        } else if (StrUtil.isNotBlank(cacheId)) {
//        存在,直接返回
            return JSONUtil.toBean(cacheId, type);
        }
        R byId = dbFallback.apply(id);
//        不存在,根据id查询数据库
        if (byId == null) {
            stringRedisTemplate.opsForValue().set(prefix + id, "", RandomNumber, unit);
//        查不到,返回商铺不存在
            return null;
        }
//        查到了,写入redis并返回
        this.set(prefix + id, byId, RandomNumber, unit);
        return byId;
    }

//        缓存击穿(逻辑过期)
    public <R, ID> R queryWithLogicalExpire(String shop_key_prefix, String shop_lock_prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long lockNumber,  Long logicalNumber, TimeUnit unit) {
//        查询商铺缓存
        String cacheId = stringRedisTemplate.opsForValue().get(shop_key_prefix + id);
//        判断是否存在
        if (StrUtil.isBlank(cacheId)) {
            return null;
        }
//        查询逻辑商铺是否过期
        RedisData beanCacheId = JSONUtil.toBean(cacheId, RedisData.class);
        JSONObject data = (JSONObject) beanCacheId.getData();
        R beanData = JSONUtil.toBean(data, type);
        if (beanCacheId.getExpireTime().isAfter(LocalDateTime.now())) {
            return beanData;
        }
//        获取互斥锁
        boolean isLock = this.tryLock(shop_lock_prefix + id, lockNumber, unit);
        if (!isLock) {
            return beanData;
        }
        R byId = dbFallback.apply(id);
//        开启独立线程
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            this.setWithLogical(shop_lock_prefix+id, JSONUtil.toJsonStr(byId), logicalNumber, unit);
            this.unLock(LOCK_SHOP_KEY + id);
        });
        return byId;
    }

    private boolean tryLock(String key, Long time, TimeUnit unit) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", unit.toSeconds(time), unit);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


//        缓存穿透 + 缓存击穿(互斥锁)
    public <R, ID> R queryWithMutex(String shop_key_prefix, String shop_lock_prefix, ID id, Long lockTimeToSeconds, Long nullTimeToSeconds, Long shopTimeToSeconds, TimeUnit unit, Class<R> type, Function<ID,R> dbFallback) {
//        查询商铺缓存
        String cacheId = stringRedisTemplate.opsForValue().get(shop_key_prefix + id);
//        判断是否存在
        if (Objects.equals(cacheId, "")) {
            return null;
        } else if (StrUtil.isNotBlank(cacheId)) {
//        存在,直接返回
            return JSONUtil.toBean(cacheId, type);
        }
        R byId = dbFallback.apply(id);
//        缓存重建
        try {
//        获取互斥锁
            boolean isLock = this.tryLock(shop_lock_prefix + id, unit.toSeconds(lockTimeToSeconds), unit);
//        判断是否成功
            if (!isLock) {
//        失败: 休眠重建
                Thread.sleep(50);
                return queryWithMutex(shop_key_prefix, shop_lock_prefix, id, lockTimeToSeconds, nullTimeToSeconds, shopTimeToSeconds, unit, type, dbFallback);
            }
//        成功: 根据id查询数据库
            if (byId == null) {
                this.set(shop_key_prefix + id, "", unit.toSeconds(nullTimeToSeconds), unit);
//        查不到,返回商铺不存在
                return null;
            }
//        查到了,写入redis并返回
            this.set(shop_key_prefix + id, JSONUtil.toJsonStr(byId), unit.toSeconds(shopTimeToSeconds), unit);
//        释放锁
            this.unLock(shop_lock_prefix + id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return byId;
    }
}
