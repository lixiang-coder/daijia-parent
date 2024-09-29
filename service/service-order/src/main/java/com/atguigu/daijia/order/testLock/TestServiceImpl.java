package com.atguigu.daijia.order.testLock;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 本地锁 synchronized
     * 优点：实现简单
     * 缺点：只在当前jvm生效，针对单机版服务。部署集群效果，锁就会失效
     */
    public synchronized void testLock1() {
        // 查询Redis中的num值
        String value = (String) this.redisTemplate.opsForValue().get("num");
        // 没有该值return
        if (StringUtils.isBlank(value)) {
            return;
        }
        // 有值就转成成int
        int num = Integer.parseInt(value);
        // 把Redis中的num值+1
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
    }

    /**
     * 分布式锁：setnx + 过期时间
     * 优点：大家用的是同一把锁，解决了集群下锁失效了问题
     * 缺点：删除可能不是自己的锁，造成锁误删
     */
    public void testLock2() {
        // 从redis中获取数据。获取当前锁  setnx
        // 设置获取时间，避免代码出现异常，导致锁无法释放
        // Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", "lock");
        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", "lock", 3, TimeUnit.SECONDS);

        // 如果获取到锁，从redis获取数据  数据 + 1 放回redis里面
        if (Boolean.TRUE.equals(ifAbsent)) {
            //获取锁成功，执行业务代码
            //1.先从redis中通过key num获取值  key提前手动设置 num 初始值：0
            String value = redisTemplate.opsForValue().get("num");
            //2.如果值为空则非法直接返回即可
            if (StringUtils.isBlank(value)) {
                return;
            }
            //3.对num值进行自增加一
            int num = Integer.parseInt(value);
            redisTemplate.opsForValue().set("num", String.valueOf(++num));

            //释放锁（有过期时间保底）
            redisTemplate.delete("lock");
        } else {
            try {
                // 重试
                Thread.sleep(100);
                this.testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 给锁加上唯一标识（UUID），防止误删
     * 缺点：拿锁，比锁，删锁不具备原子性的，删除if判断通过的时候，锁过期了，其他线程还是可以删除这个锁
     */
    public void testLock3() {
        String uuid = UUID.randomUUID().toString();
        // 从redis中获取数据。获取当前锁  setnx
        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);

        // 如果获取到锁，从redis获取数据  数据 + 1 放回redis里面
        if (Boolean.TRUE.equals(ifAbsent)) {
            //获取锁成功，执行业务代码
            //1.先从redis中通过key num获取值  key提前手动设置 num 初始值：0
            String value = redisTemplate.opsForValue().get("num");
            //2.如果值为空则非法直接返回即可
            if (StringUtils.isBlank(value)) {
                return;
            }
            //3.对num值进行自增加一
            int num = Integer.parseInt(value);
            redisTemplate.opsForValue().set("num", String.valueOf(++num));

            String redisUuid = redisTemplate.opsForValue().get("lock");
            if (uuid.equals(redisUuid)) {
                //释放锁（有过期时间保底）
                redisTemplate.delete("lock");
            }
        } else {
            try {
                // 重试
                Thread.sleep(100);
                this.testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Lua脚本保证原子性
     */
    @Override
    public void testLock() {
        String uuid = UUID.randomUUID().toString();
        // 从redis中获取数据。获取当前锁  setnx
        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);

        // 如果获取到锁，从redis获取数据  数据 + 1 放回redis里面
        if (Boolean.TRUE.equals(ifAbsent)) {
            //获取锁成功，执行业务代码
            //1.先从redis中通过key num获取值  key提前手动设置 num 初始值：0
            String value = redisTemplate.opsForValue().get("num");
            //2.如果值为空则非法直接返回即可
            if (StringUtils.isBlank(value)) {
                return;
            }
            //3.对num值进行自增加一
            int num = Integer.parseInt(value);
            redisTemplate.opsForValue().set("num", String.valueOf(++num));

            String redisUuid = redisTemplate.opsForValue().get("lock");

            //释放锁（Lua脚本实现）
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n" + "then\n" + "    return redis.call(\"del\",KEYS[1])\n" + "else\n" + "    return 0\n" + "end";
            redisScript.setScriptText(script);

            //设置返回结果
            redisScript.setResultType(Long.class);
            redisTemplate.execute(redisScript, Arrays.asList("lock"), uuid);
        } else {
            try {
                // 重试
                Thread.sleep(100);
                this.testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}