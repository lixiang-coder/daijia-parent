package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;

public interface CouponService  {

    PageVo<NoReceiveCouponVo> findNoReceivePage(Long customerId, Long page, Long limit);
}
