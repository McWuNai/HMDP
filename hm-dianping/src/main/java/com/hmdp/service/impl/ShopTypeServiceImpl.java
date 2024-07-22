package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
//        查询商户类型缓存
        Map<Object, Object> cacheList = stringRedisTemplate.opsForHash().entries(SHOP_TYPE_KEY);
//        判断是否存在
        if (!cacheList.isEmpty()) {
//        存在,直接返回
            return Result.ok(JSONUtil.toBean(cacheList.toString(), ShopType.class));
        }
        List<ShopType> bySort = query().orderByAsc("sort").list();
//        不存在,根据id查询数据库
        if (bySort.size() == 0) {
//        查不到,返回商铺类型不存在
            Result.fail("商铺不存在");
        }
//        查到了,写入redis并返回
        for (int i = 0; i < bySort.size(); i++) {
            stringRedisTemplate.opsForValue().set(SHOP_TYPE_KEY + i, JSONUtil.toJsonStr(bySort.get(i)));
        }
        return Result.ok(bySort);
    }
}
