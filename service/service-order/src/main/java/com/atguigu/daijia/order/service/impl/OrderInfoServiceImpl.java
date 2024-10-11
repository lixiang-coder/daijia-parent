package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.*;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.order.mapper.OrderBillMapper;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderProfitsharingMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.atguigu.daijia.order.service.OrderMonitorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;
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

    @Resource
    RedissonClient redissonClient;

    @Resource
    private OrderMonitorService orderMonitorService;

    @Resource
    private OrderBillMapper orderBillMapper;

    @Resource
    private OrderProfitsharingMapper orderProfitsharingMapper;

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

        // 生成订单之后，发送延迟消息
        this.sendDelayMessage(orderInfo.getId());

        // 向订单状态日志记录表插入数据
        log(orderInfo.getId(), orderInfo.getStatus());

        //向redis添加标识。接单标识，标识不存在了说明不在等待接单状态了
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK + orderInfo.getId(), "0", RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);
        return orderInfo.getId();
    }

    // 生成订单之后，发送延迟消息
    private void sendDelayMessage(Long orderId) {
        try {
            //  创建一个队列
            RBlockingDeque<Object> blockingDeque = redissonClient.getBlockingDeque("queue_cancel");
            //  将队列放入延迟队列中
            RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
            //  发送的内容
            delayedQueue.offer(orderId.toString(), 15, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
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

    // 向订单状态日志记录表插入数据
    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }

    // 司机抢单（V1.0）
    public Boolean robNewOrder1(Long driverId, Long orderId) {
        // 1.判断订单是否存在，通过redis减轻数据库压力
        if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId))) {
            // 抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        // 2.司机抢单。修改order_info表的状态值为2：代表已接单 + 司机id + 司机接单时间
        // 修改的条件：根据订单id
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

        // 3.设置参数
        orderInfo.setDriverId(driverId);
        orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
        orderInfo.setAcceptTime(new Date());

        int rows = orderInfoMapper.updateById(orderInfo);

        if (rows != 1) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        // 4.删除抢单标识
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK + orderId);
        return true;
    }

    // 司机抢单（乐观锁添加版本号方案解决并发问题）
    public Boolean robNewOrder2(Long driverId, Long orderId) {
        // 1.判断订单是否存在，通过redis减轻数据库压力
        if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId))) {
            // 抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        // 2.司机抢单。修改order_info表的状态值为2：代表已接单 + 司机id + 司机接单时间
        // update order_info set status = 2 ,driver_id = ?,accept_time = ? where id= ? and status = 1
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId);
        wrapper.eq(OrderInfo::getStatus, OrderStatus.WAITING_ACCEPT.getStatus());

        // 3.设置参数
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
        orderInfo.setDriverId(driverId);
        orderInfo.setAcceptTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);

        if (rows != 1) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        // 4.删除抢单标识
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK + orderId);
        return true;
    }

    // 司机抢单（Redisson分布式锁）
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        // 1.判断订单是否存在，通过redis减轻数据库压力
        if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId))) {
            // 抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
        // 2.初始化分布式锁，创建一个RLock实例
        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);

        try {
            // 二次判断，防止重复抢单
            if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK + orderId))) {
                //抢单失败
                throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
            }
            // 3.获取分布式锁
            boolean falg = lock.tryLock(RedisConstant.ROB_NEW_ORDER_LOCK_WAIT_TIME, RedisConstant.ROB_NEW_ORDER_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (falg) {
                // 3.司机抢单。修改order_info表的状态值为2：代表已接单 + 司机id + 司机接单时间
                // 修改的条件：根据订单id
                LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(OrderInfo::getId, orderId);
                OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

                // 4.设置参数
                orderInfo.setDriverId(driverId);
                orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
                orderInfo.setAcceptTime(new Date());
                int rows = orderInfoMapper.updateById(orderInfo);

                if (rows != 1) {
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }
                // 5.删除抢单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK + orderId);
            }
        } catch (Exception e) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        } finally {
            // 6.释放分布式锁
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
        return true;
    }

    // 乘客端查找当前订单
    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        //封装条件：乘客id
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getCustomerId, customerId);

        //各种状态
        Integer[] statusArray = {OrderStatus.ACCEPTED.getStatus(), OrderStatus.DRIVER_ARRIVED.getStatus(), OrderStatus.UPDATE_CART_INFO.getStatus(), OrderStatus.START_SERVICE.getStatus(), OrderStatus.END_SERVICE.getStatus(), OrderStatus.UNPAID.getStatus()};

        wrapper.in(OrderInfo::getStatus, statusArray);

        //获取最新一条记录（将id倒序排列，取第一条数据）
        wrapper.orderByDesc(OrderInfo::getId);
        wrapper.last(" limit 1");

        //调用方法: select * from order_info where customerId = ? and status in (1,2,3...) limit 1
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

        //封装到 CurrentOrderInfoVo
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();

        if (orderInfo != null) {
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }

        return currentOrderInfoVo;
    }

    // 司机端查找当前订单
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        //封装条件：乘客id
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getDriverId, driverId);

        //各种状态
        Integer[] statusArray = {OrderStatus.ACCEPTED.getStatus(), OrderStatus.DRIVER_ARRIVED.getStatus(), OrderStatus.UPDATE_CART_INFO.getStatus(), OrderStatus.START_SERVICE.getStatus(), OrderStatus.END_SERVICE.getStatus(), OrderStatus.UNPAID.getStatus()};

        wrapper.in(OrderInfo::getStatus, statusArray);

        //获取最新一条记录（将id倒序排列，取第一条数据）
        wrapper.orderByDesc(OrderInfo::getId);
        wrapper.last(" limit 1");

        //调用方法: select * from order_info where driverId = ? and status in (1,2,3...) limit 1
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);

        //封装到 CurrentOrderInfoVo
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();

        if (orderInfo != null) {
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }

        return currentOrderInfoVo;
    }

    // 司机到达起始点
    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        // 更新订单状态和到达时间，条件：orderId + driverId
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId);
        wrapper.eq(OrderInfo::getDriverId, driverId);

        // 设置更新的参数 update order_info set orderStatus = ? , arriveTime = ? where orderId = ? and driverId = ?
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.DRIVER_ARRIVED.getStatus());
        orderInfo.setArriveTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);
        if (rows == 1) {
            //记录日志
            this.log(orderId, OrderStatus.DRIVER_ARRIVED.getStatus());
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    // 司机更新代驾车辆信息
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, updateOrderCartForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId, updateOrderCartForm.getDriverId());

        OrderInfo updateOrderInfo = new OrderInfo();
        BeanUtils.copyProperties(updateOrderCartForm, updateOrderInfo);
        updateOrderInfo.setStatus(OrderStatus.UPDATE_CART_INFO.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(updateOrderCartForm.getOrderId(), OrderStatus.UPDATE_CART_INFO.getStatus());
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    // 开始代驾服务
    @Override
    public Boolean startDriver(StartDriveForm startDriveForm) {
        // 根据订单id  +  司机id  更新订单状态  和 开始代驾时间
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, startDriveForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId, startDriveForm.getDriverId());

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.START_SERVICE.getStatus());
        orderInfo.setArriveTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);
        if (rows == 1) {
            //记录日志
            this.log(startDriveForm.getOrderId(), OrderStatus.START_SERVICE.getStatus());
            //return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }

        //初始化订单监控统计数据
        OrderMonitor orderMonitor = new OrderMonitor();
        orderMonitor.setOrderId(startDriveForm.getOrderId());
        orderMonitorService.saveOrderMonitor(orderMonitor);
        return true;
    }

    // 根据时间段获取订单数
    @Override
    public Long getOrderNumByTime(String startTime, String endTime) {
        // 09 <= time < 10
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ge(OrderInfo::getStartServiceTime, startTime);
        queryWrapper.lt(OrderInfo::getStartServiceTime, endTime);
        Long count = orderInfoMapper.selectCount(queryWrapper);
        return count;
    }

    // 结束代驾服务更新订单账单
    @Override
    public Boolean endDrive(UpdateOrderBillForm updateOrderBillForm) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, updateOrderBillForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId, updateOrderBillForm.getDriverId());
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.END_SERVICE.getStatus());
        updateOrderInfo.setRealAmount(updateOrderBillForm.getTotalAmount());
        updateOrderInfo.setFavourFee(updateOrderBillForm.getFavourFee());
        updateOrderInfo.setEndServiceTime(new Date());
        updateOrderInfo.setRealDistance(updateOrderBillForm.getRealDistance());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(updateOrderBillForm.getOrderId(), OrderStatus.END_SERVICE.getStatus());

            //插入实际账单数据
            OrderBill orderBill = new OrderBill();
            BeanUtils.copyProperties(updateOrderBillForm, orderBill);
            orderBill.setOrderId(updateOrderBillForm.getOrderId());
            orderBill.setPayAmount(orderBill.getTotalAmount());
            orderBillMapper.insert(orderBill);

            //插入分账信息数据
            OrderProfitsharing orderProfitsharing = new OrderProfitsharing();
            BeanUtils.copyProperties(updateOrderBillForm, orderProfitsharing);
            orderProfitsharing.setOrderId(updateOrderBillForm.getOrderId());
            orderProfitsharing.setRuleId(updateOrderBillForm.getProfitsharingRuleId());
            orderProfitsharing.setStatus(1);
            orderProfitsharingMapper.insert(orderProfitsharing);
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    // 获取乘客订单分页列表
    @Override
    public PageVo findCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectCustomerOrderPage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    // 获取司机订单分页列表
    @Override
    public PageVo findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectDriverOrderPage(pageParam, driverId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    // 根据订单id获取实际账单信息
    @Override
    public OrderBillVo getOrderBillInfo(Long orderId) {
        OrderBill orderBill = orderBillMapper.selectOne(new LambdaQueryWrapper<OrderBill>().eq(OrderBill::getOrderId, orderId));
        OrderBillVo orderBillVo = new OrderBillVo();
        BeanUtils.copyProperties(orderBill, orderBillVo);
        return orderBillVo;
    }

    // 根据订单id获取实际分账信息
    @Override
    public OrderProfitsharingVo getOrderProfitsharing(Long orderId) {
        OrderProfitsharing orderProfitsharing = orderProfitsharingMapper.selectOne(new LambdaQueryWrapper<OrderProfitsharing>().eq(OrderProfitsharing::getOrderId, orderId));
        OrderProfitsharingVo orderProfitsharingVo = new OrderProfitsharingVo();
        BeanUtils.copyProperties(orderProfitsharing, orderProfitsharingVo);
        return orderProfitsharingVo;
    }

    // 发送账单信息
    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.UNPAID.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(orderId, OrderStatus.UNPAID.getStatus());
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    // 获取订单支付信息
    @Override
    public OrderPayVo getOrderPayVo(String orderNo, Long customerId) {
        OrderPayVo orderPayVo = orderInfoMapper.selectOrderPayVo(orderNo, customerId);
        if (null != orderPayVo) {
            String content = orderPayVo.getStartLocation() + " 到 " + orderPayVo.getEndLocation();
            orderPayVo.setContent(content);
        }
        return orderPayVo;
    }

    // 更改订单支付状态
    @Override
    public Boolean updateOrderPayStatus(String orderNo) {
        //查询订单，判断订单状态，如果已更新支付状态，直接返回
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getOrderNo, orderNo);
        queryWrapper.select(OrderInfo::getId, OrderInfo::getDriverId, OrderInfo::getStatus);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);

        if (null == orderInfo || orderInfo.getStatus().intValue() == OrderStatus.PAID.getStatus().intValue()) {
            return true;
        }

        //更新订单状态
        LambdaQueryWrapper<OrderInfo> updateQueryWrapper = new LambdaQueryWrapper<>();
        updateQueryWrapper.eq(OrderInfo::getOrderNo, orderNo);
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.PAID.getStatus());
        updateOrderInfo.setPayTime(new Date());

        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(orderInfo.getId(), OrderStatus.PAID.getStatus());
        } else {
            log.error("订单支付回调更新订单状态失败，订单号为：" + orderNo);
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    // 获取订单的系统奖励
    @Override
    public OrderRewardVo getOrderRewardFee(String orderNo) {
        //根据系统编号查询订单表
        OrderInfo orderInfo = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo).select(OrderInfo::getId, OrderInfo::getDriverId));
        //根据订单id查询系统奖励表
        OrderBill orderBill = orderBillMapper.selectOne(new LambdaQueryWrapper<OrderBill>().eq(OrderBill::getOrderId, orderInfo.getId()).select(OrderBill::getRewardFee));

        //封装到vo里面
        OrderRewardVo orderRewardVo = new OrderRewardVo();
        orderRewardVo.setOrderId(orderInfo.getId());
        orderRewardVo.setDriverId(orderInfo.getDriverId());
        orderRewardVo.setRewardFee(orderBill.getRewardFee());
        return orderRewardVo;
    }

    // 根据订单Id 取消订单
    @Override
    public void orderCancel(long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (Objects.equals(orderInfo.getStatus(), OrderStatus.WAITING_ACCEPT.getStatus())) {
            // 设置更新数据
            OrderInfo orderInfoUpt = new OrderInfo();
            orderInfoUpt.setId(orderId);
            orderInfoUpt.setStatus(OrderStatus.CANCEL_ORDER.getStatus());
            // 执行更新方法
            int rows = orderInfoMapper.updateById(orderInfoUpt);

            if (rows == 1) {
                //删除redis订单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            }
        }
    }

    // 更新订单优惠券金额
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean updateCouponAmount(Long orderId, BigDecimal couponAmount) {
        int row = orderBillMapper.updateCouponAmount(orderId, couponAmount);
        if (row != 1) {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }
}