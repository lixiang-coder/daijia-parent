package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;

    @Resource
    private NewOrderFeignClient newOrderFeignClient;


    //根据订单id获取订单状态
    @Override
    public Integer getOrderStatus(Long orderId) {
        return orderInfoFeignClient.getOrderStatus(orderId).getData();
    }

    // 查询司机新订单数据
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        List<NewOrderDataVo> newOrderDataVoList = newOrderFeignClient.findNewOrderQueueData(driverId).getData();
        return newOrderDataVoList;
    }

    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        // 远程调用
        Boolean result = orderInfoFeignClient.robNewOrder(driverId, orderId).getData();
        return result;
    }

    // 司机端查找当前订单
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        // 远程调用
        CurrentOrderInfoVo currentOrderInfoVo = orderInfoFeignClient.searchDriverCurrentOrder(driverId).getData();
        return currentOrderInfoVo;
    }
}
