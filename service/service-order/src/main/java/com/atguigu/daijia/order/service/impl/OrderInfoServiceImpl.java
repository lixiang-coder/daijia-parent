package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private OrderInfoMapper orderInfoMapper;

    @Resource
    OrderStatusLogMapper orderStatusLogMapper;

    @Resource
    private RedisTemplate redisTemplate;

    //乘客下单（保存订单信息）
    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm, orderInfo);

        // 补充属性
        orderInfo.setOrderNo(UUID.randomUUID().toString().replaceAll("-", ""));     //订单号
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());    //订单状态
        // 向orderInfo表中插入数据
        orderInfoMapper.insert(orderInfo);

        // 向订单状态日志记录表插入数据
        log(orderInfo.getId(), orderInfo.getStatus());

        //向redis添加标识。接单标识，标识不存在了说明不在等待接单状态了
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK + orderInfo.getId(), "0", RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);
        return orderInfo.getId();
    }

    // 根据订单id获取订单状态
    @Override
    public Integer getOrderStatus(Long orderId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId);
        wrapper.select(OrderInfo::getStatus);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        if (orderInfo == null) {
            //返回null，feign解析会抛出异常，给默认值，后续会用
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }

    // 司机抢单
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        // 1.判断订单是否存在，通过redis减轻数据库压力
        if (!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId)) {
            // 抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
        // 2.司机抢单。修改order_info表的状态值为2：代表已接单 + 司机id + 司机接单时间
        // 修改的条件：根据订单id
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        // 设置参数
        orderInfo.setDriverId(driverId);
        orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
        orderInfo.setAcceptTime(new Date());
        int rows = orderInfoMapper.updateById(orderInfo);

        if (rows != 1) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
        //删除抢单标识
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK + orderId);
        return true;
    }

    // 向订单状态日志记录表插入数据
    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}
