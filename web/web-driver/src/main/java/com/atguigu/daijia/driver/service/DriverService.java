package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;

public interface DriverService {


    String login(String code);

    DriverLoginVo getDriverLoginInfo(Long driverId);

    DriverAuthInfoVo getDriverAuthInfo(Long driverId);
}
