package com.atguigu.daijia.map.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "service-map")
public interface MapFeignClient {


}