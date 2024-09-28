package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
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

    // 获取司机登录信息
    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        // 自定义Feign结果解析，避免了重复校验200和用户id不为空
        return driverInfoFeignClient.getDriverLoginInfo(driverId).getData();
    }

    // 获取司机认证信息
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        // 自定义Feign结果解析，避免了重复校验200和用户id不为空
        DriverAuthInfoVo driverAuthInfoVo = driverInfoFeignClient.getDriverAuthInfo(driverId).getData();
        return driverAuthInfoVo;
    }

    // 更新司机认证信息
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        // 自定义Feign结果解析，避免了重复校验200和用户id不为空
        Boolean result = driverInfoFeignClient.updateDriverAuthInfo(updateDriverAuthInfoForm).getData();
        return result;
    }

    // 创建司机人脸模型
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        // 自定义Feign结果解析，避免了重复校验200和用户id不为空
        Boolean result = driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm).getData();
        return result;
    }

    // 判断司机当日是否进行过人脸识别
    @Override
    public Boolean isFaceRecognition(Long driverId) {
        // 远程调用
        Boolean result = driverInfoFeignClient.isFaceRecognition(driverId).getData();
        return result;
    }
}
