package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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

    @Resource
    private MapFeignClient mapFeignClient;


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

    // 获取订单账单详细信息
    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long driverId) {
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        // 判断：是不是司机自己的订单
        if (orderInfo.getDriverId().longValue() != driverId.longValue()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //封装订单信息
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        orderInfoVo.setOrderId(orderId);
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        return orderInfoVo;
    }

    // 计算最佳驾驶线路
    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        DrivingLineVo drivingLineVo = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
        return drivingLineVo;
    }

    // 司机到达代驾起始地点
    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        return orderInfoFeignClient.driverArriveStartLocation(orderId, driverId).getData();
    }
}
