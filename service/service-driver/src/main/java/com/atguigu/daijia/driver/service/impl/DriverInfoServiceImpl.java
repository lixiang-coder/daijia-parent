package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.driver.mapper.DriverAccountMapper;
import com.atguigu.daijia.driver.mapper.DriverInfoMapper;
import com.atguigu.daijia.driver.mapper.DriverLoginLogMapper;
import com.atguigu.daijia.driver.mapper.DriverSetMapper;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.DriverAccount;
import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.entity.driver.DriverLoginLog;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.beans.BeanProperty;
import java.math.BigDecimal;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverInfoServiceImpl extends ServiceImpl<DriverInfoMapper, DriverInfo> implements DriverInfoService {

    @Resource
    private WxMaService wxMaService;

    @Resource
    private DriverInfoMapper driverInfoMapper;

    @Resource
    private DriverLoginLogMapper driverLoginLogMapper;

    @Resource
    private DriverSetMapper driverSetMapper;

    @Resource
    private DriverAccountMapper driverAccountMapper;

    @Resource
    private CosService cosService;


    @Override
    public Long login(String code) {
        //1 根据code值 + 小程序id + 密钥请求微信接口，返回openid
        String openid = null;
        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            openid = sessionInfo.getOpenid();

            //2 根据openid查询数据库表，判断是否第一次登录。如果openid不存在返回null，如果存在返回一条记录
            //select * from driver_info di where di.wx_open_id = ''
            LambdaQueryWrapper<DriverInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DriverInfo::getWxOpenId, openid);
            DriverInfo driverInfo = driverInfoMapper.selectOne(wrapper);

            //3 如果第一次登录，添加信息到司机表
            if (driverInfo == null) {
                driverInfo = new DriverInfo();
                driverInfo.setNickname(String.valueOf(System.currentTimeMillis()));
                driverInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
                driverInfo.setWxOpenId(openid);
                driverInfoMapper.insert(driverInfo);

                // 初始化司机设置
                DriverSet driverSet = new DriverSet();
                driverSet.setDriverId(driverInfo.getId());
                driverSet.setOrderDistance(new BigDecimal(0));//0：无限制
                driverSet.setAcceptDistance(new BigDecimal(SystemConstant.ACCEPT_DISTANCE));//默认接单范围：5公里
                driverSet.setIsAutoAccept(0);//0：否 1：是
                driverSetMapper.insert(driverSet);


                // 初始化司机账户信息
                DriverAccount driverAccount = new DriverAccount();
                driverAccount.setDriverId(driverInfo.getId());
                driverAccountMapper.insert(driverAccount);
            }

            //4 记录司机登录日志信息
            DriverLoginLog driverLoginLog = new DriverLoginLog();
            driverLoginLog.setDriverId(driverInfo.getId());
            driverLoginLog.setMsg("小程序司机端登录");
            driverLoginLogMapper.insert(driverLoginLog);

            //5 返回司机id
            return driverLoginLog.getId();
        } catch (WxErrorException e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    // 获取司机登录信息
    @Override
    public DriverLoginVo getDriverInfo(Long driverId) {
        //1 判断driverId参数是否合法
        if (driverId == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //2 根据司机id查询用户信息。select * from driver_info di where di.driverId = ''
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);

        //3 封装到 DriverLoginVo
        DriverLoginVo driverLoginVo = new DriverLoginVo();
        BeanUtils.copyProperties(driverInfo, driverLoginVo);

        // DriverLoginVo 中有一个特殊字段 isArchiveFace，所以要处理一下
        String faceModelId = driverInfo.getFaceModelId();   // 只要这个不为空，那就在VO中为true

        boolean isArchiveFace = StringUtils.hasText(faceModelId);
        // 有手机号则true，否则反之
        driverLoginVo.setIsArchiveFace(isArchiveFace);

        // 返回
        return driverLoginVo;
    }

    // 获取司机认证信息
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        //1 判断driverId参数是否合法
        if (driverId == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //2 根据司机id查询用户信息。select * from driver_info di where di.driverId = ''
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        //3 封装到 DriverAuthInfoVo
        DriverAuthInfoVo driverAuthInfoVo = new DriverAuthInfoVo();
        BeanUtils.copyProperties(driverInfo, driverAuthInfoVo);

        //4 将DriverInfo中的图片地址(腾讯云的地址)传入，生成一个临时访问的地址(真实的网址)
        String idcardBackUrl = driverInfo.getIdcardBackUrl();
        String tempURL = cosService.getImageUrl(idcardBackUrl);
        driverAuthInfoVo.setIdcardBackShowUrl(tempURL);

        driverAuthInfoVo.setIdcardFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardFrontUrl()));
        driverAuthInfoVo.setIdcardHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardHandUrl()));
        driverAuthInfoVo.setDriverLicenseFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseFrontUrl()));
        driverAuthInfoVo.setDriverLicenseBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseBackUrl()));
        driverAuthInfoVo.setDriverLicenseHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseHandUrl()));

        return driverAuthInfoVo;
    }

    // 更新司机认证信息
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        // 获取司机id
        Long driverId = AuthContextHolder.getUserId();

        // 修改信息
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.setId(driverId);
        BeanUtils.copyProperties(updateDriverAuthInfoForm,driverInfo);

        boolean update = updateById(driverInfo);
        return update;
    }
}