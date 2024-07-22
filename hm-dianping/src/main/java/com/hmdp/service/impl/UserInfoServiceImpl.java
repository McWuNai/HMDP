package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
@Slf4j
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return  Result.fail("手机号格式不正确");
        }
//        生成验证码 | 保存验证码到Session
        String randomNumbers = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, randomNumbers, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("验证码发送成功,验证码: " + randomNumbers);
//        返回ok(result返回值)
        return Result.ok();
    }

    @Override
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        int monthValue = now.getMonthValue();
        int dayOfMonth = now.getDayOfMonth();
        int year = now.getYear();
        String key = USER_SIGN_KEY + year + ":" + monthValue + ":" + id;
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        int monthValue = now.getMonthValue();
        int dayOfMonth = now.getDayOfMonth();
        int year = now.getYear();
        String key = USER_SIGN_KEY + year + ":" + monthValue + ":" + id;
        int signCount = 0;
        for (int i = dayOfMonth-1; i > 0; i--) {
            Boolean bit = stringRedisTemplate.opsForValue().getBit(key, i);
            if (Boolean.TRUE.equals(bit)) {
                signCount++;
            }
        }
        return Result.ok(signCount);
    }

}
