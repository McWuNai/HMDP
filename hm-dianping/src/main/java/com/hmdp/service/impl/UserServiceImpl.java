package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

//        校验手机号和验证码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return  Result.fail("手机号格式不正确");
        }
//          从Redis获取验证码
        String cacheRandomNumbers = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
//        校验验证码
        if (cacheRandomNumbers == null || !cacheRandomNumbers.equals(code)) {
            Result.fail("验证码不能为空");
        }
//        查找用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
//       随机生成Token
        String token = UUID.randomUUID().toString(true);
//        转类为DTO，避免存储过度冗余数据
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//        删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
//        储存到Redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
//        设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
//        统计UV访问数据
        stringRedisTemplate.opsForHyperLogLog().add("login:count:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd")), String.valueOf(user.getId()));
//        设置session请求头值
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String authorization = request.getHeader("authorization");
        stringRedisTemplate.opsForHash().delete(LOGIN_USER_KEY + authorization, "icon", "nickName", "id");
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User()
                .setPhone(phone)
                .setNickName(
                        USER_NICK_NAME_PREFIX + RandomUtil.randomString(10)
                );
        save(user);
        return user;
    }
}
