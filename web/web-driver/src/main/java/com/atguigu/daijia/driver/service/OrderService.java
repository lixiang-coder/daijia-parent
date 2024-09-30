package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;

import java.util.List;

public interface OrderService {


    Integer getOrderStatus(Long orderId);

    List<NewOrderDataVo> findNewOrderQueueData(Long driverId);

    Boolean robNewOrder(Long driverId, Long orderId);

    CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId);
}
