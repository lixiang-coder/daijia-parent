package com.atguigu.daijia.driver.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "service-driver")
public interface DriverInfoFeignClient {

    /**
     * 小程序授权登录
     *
     * @param code
     * @return
     */
    @GetMapping("/driver/info/login/{code}")
    Result<Long> login(@PathVariable String code);


    /**
     * 获取司机登录信息
     *
     * @param driverId
     * @return
     */
    @GetMapping("/driver/info/getDriverLoginInfo/{driverId}")
    Result<DriverLoginVo> getDriverLoginInfo(@PathVariable Long driverId);

    /**
     * 获取司机认证信息
     *
     * @param driverId
     * @return
     */
    @GetMapping("/driver/info/getDriverAuthInfo/{driverId}")
    Result<DriverAuthInfoVo> getDriverAuthInfo(@PathVariable Long driverId);
}