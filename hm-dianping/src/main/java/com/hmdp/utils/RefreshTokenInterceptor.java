package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
//        获取请求头信息
        String token = request.getHeader("authorization");
//        判断token是否为空
        if (StrUtil.isBlank(token)) {
            return true;
        }
//        根据请求头获取hash数据
        Map<Object, Object> usermap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        判断用户是否存在
        if (usermap.isEmpty()) {
            return true;
        }
//        将获取到的hash数据类型转为UserDTO类型
        UserDTO userDTO = BeanUtil.fillBeanWithMap(usermap, new UserDTO(), false);
//        将UserDTO类型的数据存储到threadLocal里边
        UserHolder.saveUser(userDTO);
//        重新设置该用户的token数据有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
//        将token储存到session中
        HttpSession session = request.getSession(true);
        session.setAttribute("authorization", token);
//        放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
//        在完成之后删除用户,避免造成数据冗余
        UserHolder.removeUser();
    }
}
