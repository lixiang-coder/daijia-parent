package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {
    @Resource
    private LocationFeignClient locationFeignClient;

    @Resource
    DriverInfoFeignClient driverInfoFeignClient;


    // 开启接单服务：更新司机经纬度位置
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        // 1.开启接单了才能更新司机接单位置
        Result<DriverSet> driverSetResult = driverInfoFeignClient.getDriverSet(updateDriverLocationForm.getDriverId());
        DriverSet driverSet = driverSetResult.getData();
        Integer serviceStatus = driverSet.getServiceStatus();
        // 判断司机有没有开始接单
        if (serviceStatus == null || serviceStatus != 1){
            throw new GuiguException(ResultCodeEnum.NO_START_SERVICE);
        }

        // 2.远程调用
        Result<Boolean> booleanResult = locationFeignClient.updateDriverLocation(updateDriverLocationForm);
        return booleanResult.getData();
    }
}
