package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.checklogin.XZYLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "司机API接口管理")
@RestController
@RequestMapping(value = "/driver")
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverController {

    @Resource
    private DriverService driverService;

    @Operation(summary = "小程序司机端授权登录")
    @GetMapping("/login/{code}")
    public Result<String> login(@PathVariable String code) {
        return Result.ok(driverService.login(code));
    }

    @Operation(summary = "获取司机登录信息")
    @XZYLogin
    @GetMapping("/getDriverLoginInfo")
    public Result<DriverLoginVo> getDriverLoginInfo(){
        Long driverId = AuthContextHolder.getUserId();
        // 调用service
        DriverLoginVo driverLoginVo = driverService.getDriverLoginInfo(driverId);
        return Result.ok(driverLoginVo);
    }

    @Operation(summary = "获取司机认证信息")
    @XZYLogin
    @GetMapping("/getDriverAuthInfo/")
    public Result<DriverAuthInfoVo> getDriverAuthInfo() {
        // 获取当前登录司机的id
        Long driverId = AuthContextHolder.getUserId();
        DriverAuthInfoVo driverAuthInfoVo = driverService.getDriverAuthInfo(driverId);
        return Result.ok(driverAuthInfoVo);
    }
}

