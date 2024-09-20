package com.atguigu.daijia.customer.client;

import com.atguigu.daijia.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "service-customer")
public interface CustomerInfoFeignClient {

    /**
     * 小程序授权登录
     */
    @GetMapping("/customer/info/login/{code}")
    Result<Long> login(@PathVariable String code);
}