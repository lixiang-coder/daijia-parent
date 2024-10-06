package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/customer/info")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerInfoController {

    @Resource
    private CustomerInfoService customerInfoService;


    @Operation(summary = "小程序授权登录")
    @GetMapping("/login/{code}")
    public Result<Long> login(@PathVariable String code) {
        return Result.ok(customerInfoService.login(code));
    }

    @Operation(summary = "获取客户登录信息")
    @GetMapping("/getCustomerLoginInfo/{customerId}")
    public Result<CustomerLoginVo> getCustomerLoginInfo(@PathVariable Long customerId) {
        CustomerLoginVo customerLoginInfo = customerInfoService.getCustomerLoginInfo(customerId);
        return Result.ok(customerLoginInfo);
    }


    @Operation(summary = "获取客户基本信息")
    @GetMapping("/getCustomerInfo/{customerId}")
    public Result<CustomerInfo> getCustomerInfo(@PathVariable Long customerId) {
        return Result.ok(customerInfoService.getById(customerId));
    }

    @Operation(summary = "更新客户微信手机号码")
    @PostMapping("/updateWxPhoneNumber")
    public Result<Boolean> updateWxPhoneNumber(@RequestBody UpdateWxPhoneForm updateWxPhoneForm) {
        return Result.ok(customerInfoService.updateWxPhoneNumber(updateWxPhoneForm));
    }

    @Operation(summary = "获取客户OpenId")
    @GetMapping("/getCustomerOpenId/{customerId}")
    public Result<String> getCustomerOpenId(@PathVariable Long customerId) {
        return Result.ok(customerInfoService.getCustomerOpenId(customerId));
    }
}

