package com.atguigu.daijia.order.mapper;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.vo.order.OrderListVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    // 查询乘客订单分页
    IPage<OrderListVo> selectCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId);
}
