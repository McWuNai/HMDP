package com.hmdp.utils;

import org.springframework.stereotype.Component;

@Component
public interface ILock {
//    获取锁
    boolean tryLock (long timeoutSec);
//    释放锁
    void unLock();
}
