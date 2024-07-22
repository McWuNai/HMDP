package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RabbitMQUtils;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private static final DefaultRedisScript<Long> ISCOUNT_SCRIPT;

    private static final ExecutorService secKill_order_executor = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;
    static {
        ISCOUNT_SCRIPT = new DefaultRedisScript<>();
        ISCOUNT_SCRIPT.setLocation(new ClassPathResource("isCount.lua"));
        ISCOUNT_SCRIPT.setResultType(Long.class);
    }

    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    @Resource
    RedisIdWorker redisIdWorker;
    @Resource
    RedissonClient redissonClient;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    RabbitMQUtils rabbitMQUtils;
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    private void handlerVoucherOrder(VoucherOrder take) {
        Long userId = take.getUserId();
        RLock Lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = Lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return ;
        }
        try {
            proxy.createVoucherOrder(take);
        } finally {
            Lock.unlock();
        }

    }

    @PostConstruct
    private void init() {
        secKill_order_executor.submit(new VoucherOrderHandler());
    }

    @Override
    public Result seckillVouther(Long voucherId) {
//        获取信息
        Long userId = UserHolder.getUser().getId();
        long nextId = redisIdWorker.nextId("order");
/*        SeckillVoucher byId = iSeckillVoucherService.getById(voucherId);
        if (byId.getBeginTime().isAfter(LocalDateTime.now()) || byId.getEndTime().isBefore(LocalDateTime.now()))
            return Result.fail("活动未开始或已结束");*/
//        启动Lua脚本
        Long count = stringRedisTemplate.execute(
                ISCOUNT_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int i = count.intValue();
        if (i != 0) {
            return Result.fail(i == 1 ? "优惠券已被抢完" : "不能重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setId(nextId);
        orderTasks.add(voucherOrder);
//            事务代理，否则该方法不会启用事务，保证不了原子性
        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        rabbitMQUtils.send("exchange.direct.order", "order", voucherOrder);

//        生成订单编号
        return Result.ok(nextId);
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder take = orderTasks.take();
                    handlerVoucherOrder(take);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

/*//队列监听
    @PostConstruct
    @RabbitListener(queues = "queue.order")
    public void receive(Object... message) {

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder
                .setUserId((Long) message[0])
                .setVoucherId((Long) message[1])
                .setId((Long) message[2]);
        iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", message[1])
                .gt("stock", 0).update();
        save(voucherOrder);
    }*/

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder take) {
        Long voucherId = take.getVoucherId();
        Long userId = take.getUserId();
//        判断用户是否已领取过
        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) log.error("已领取过该优惠券");
//        还有则减清库存
        boolean voucherId1 = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!voucherId1) log.error("优惠券已被抢完");
//        创建订单
        save(take);
    }
}
