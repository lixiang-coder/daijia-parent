package com.atguigu.daijia.coupon.mapper;

import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponInfoMapper extends BaseMapper<CouponInfo> {

    // 查询乘客未领取优惠卷
    IPage<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, @Param("customerId") Long customerId);

    // 查询未使用优惠券分页列表
    IPage<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId);

    // 查询已使用优惠券分页列表
    IPage<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId);

    // 更新领取数量
    int updateReceiveCount(Long couponId);

    int updateReceiveCountByLimit(Long couponId);

    // 获取未使用的优惠券列表
    List<NoUseCouponVo> findNoUseList(Long customerId);

    // 更新使用次数
    int updateUseCount(Long id);
}
