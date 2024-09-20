package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.checklogin.XZYLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "客户API接口管理")
@RestController
@RequestMapping("/customer")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerController {

    @Resource
    private CustomerService customerInfoService;

    @Operation(summary = "小程序客户端授权登录")
    @GetMapping("/login/{code}")
    public Result<String> wxLogin(@PathVariable String code) {
        return Result.ok(customerInfoService.login(code));
    }

    /*@Operation(summary = "获取客户登录信息")
    @GetMapping("/getCustomerLoginInfo")
    public Result<CustomerLoginVo> getCustomerLoginInfo(@RequestHeader(value = "token") String token) {
        //1 从请求头中获取token
        //HttpServletRequest request        String token1 = request.getHeader("token");

        // 调用service
        CustomerLoginVo customerLoginVo = customerInfoService.getCustomerLoginInfo(token);

        return Result.ok(customerLoginVo);
    }*/

    @Operation(summary = "获取客户登录信息")
    @XZYLogin   //自定义登录校验注解
    @GetMapping("/getCustomerLoginInfo")
    public Result<CustomerLoginVo> getCustomerLoginInfo() {
        // 直接从ThreadLocal里面获取用户id
        Long customerId = AuthContextHolder.getUserId();
        // 调用service
        CustomerLoginVo customerLoginVo = customerInfoService.getCustomerLoginInfo(customerId);

        return Result.ok(customerLoginVo);
    }

    @Operation(summary = "更新客户微信手机号码")
    @XZYLogin
    @PostMapping("/updateWxPhone")
    public Result updateWxPhone(@RequestBody UpdateWxPhoneForm updateWxPhoneForm) {
        // 直接从ThreadLocal里面获取用户id
        updateWxPhoneForm.setCustomerId(AuthContextHolder.getUserId());
        // 注：微信公众号个人版不能获取手机号，直接跳过
        //customerInfoService.updateWxPhoneNumber(updateWxPhoneForm);
        return Result.ok(true);
    }

}

