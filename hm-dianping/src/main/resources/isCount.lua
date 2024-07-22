local voucherId = ARGV[1];
local userId = ARGV[2];
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
-- 不充足则返回1
    return 1
end
-- 充足则判断之前是否下过单
-- 将当前用户ID存入set集合,获取返回值，0为已下单, 1为未下单
if(redis.call('sadd', orderKey, userId) == 0) then
-- 是则返回2
    return 2
end
-- 否则返回扣减库存
redis.call('incrby', stockKey, -1)
return 0