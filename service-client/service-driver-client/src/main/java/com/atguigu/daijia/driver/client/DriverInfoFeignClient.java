package com.atguigu.daijia.driver.client;

import com.atguigu.daijia.common.result.Result;
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
}