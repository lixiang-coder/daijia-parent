package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverServiceImpl implements DriverService {
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;

    @Resource
    private RedisTemplate redisTemplate;


    @Override
    public String login(String code) {
        //1 拿着code进行远程调用，返回司机id
        Long driverId = driverInfoFeignClient.login(code).getData();

        //2 生成token字符串
        String token = UUID.randomUUID().toString().replaceAll("-", "");

        //3 把用户id放到Redis，设置过期时间。    key:token  value:driverId
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token,
                driverId.toString(),
                RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                TimeUnit.SECONDS);

        return token;
    }
}
