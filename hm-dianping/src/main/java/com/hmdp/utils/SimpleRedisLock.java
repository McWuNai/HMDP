package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;

public class SimpleRedisLock implements ILock{

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    private static final DefaultRedisScript<Long> SETLOCK_SCRIPT;
    static {
        SETLOCK_SCRIPT = new DefaultRedisScript<>();
        SETLOCK_SCRIPT.setLocation(new ClassPathResource("setLock.lua"));
        SETLOCK_SCRIPT.setResultType(Long.class);

        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
////        获取当前线程与随机UUID作为value，保证唯一性
//        String value = ID_PREFIX + Thread.currentThread().getId();
////        上锁
//        Boolean setIfAbsent = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + name, value, timeoutSec, TimeUnit.SECONDS);
////        当锁已存在返回false
//        return !BooleanUtil.isFalse(setIfAbsent);
        Long execute = stringRedisTemplate.execute(
                SETLOCK_SCRIPT,
                Collections.singletonList("lock:" + name),
                ID_PREFIX + Thread.currentThread().getId());
        return execute != 0;
    }

    @Override
    @Transactional
    public void unLock() {
//        调用Lua脚本启动，确保解锁的原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList("lock:" + name),
                ID_PREFIX + Thread.currentThread().getId());
////        获取线程标识
//        String value = ID_PREFIX + Thread.currentThread().getId();
//        String s = stringRedisTemplate.opsForValue().get("lock:" + name);
////        判断线程标识是否一致，是则解锁，否则不做任何改动
//        if (value.equals(s)) stringRedisTemplate.delete("lock:" + name);

    }
}
