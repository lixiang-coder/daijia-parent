package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.config.TencentCloudProperties;
import com.atguigu.daijia.driver.mapper.*;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.*;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20180301.IaiClient;
import com.tencentcloudapi.iai.v20180301.models.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;

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

    @Resource
    private TencentCloudProperties tencentCloudProperties;

    @Resource
    private DriverFaceRecognitionMapper driverFaceRecognitionMapper;


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
            return driverInfo.getId();
        } catch (WxErrorException e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    // 获取司机登录信息
    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
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
        //Long driverId1 = AuthContextHolder.getUserId();
        Long driverId = updateDriverAuthInfoForm.getDriverId();

        // 修改信息
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.setId(driverId);
        BeanUtils.copyProperties(updateDriverAuthInfoForm, driverInfo);

        boolean update = updateById(driverInfo);
        return update;
    }

    // 创建司机人脸模型
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        // 根据司机id获取司机信息
        DriverInfo driverInfo = driverInfoMapper.selectById(driverFaceModelForm.getDriverId());
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            CreatePersonRequest req = new CreatePersonRequest();

            // 设置相关值(人员库ID)
            req.setGroupId(tencentCloudProperties.getPersonGroupId());
            // 基本信息
            req.setPersonId(String.valueOf(driverInfo.getId()));
            req.setGender(Long.parseLong(driverInfo.getGender()));
            req.setQualityControl(4L);
            req.setUniquePersonControl(4L);
            req.setPersonName(driverInfo.getName());
            req.setImage(driverFaceModelForm.getImageBase64());

            // 返回的resp是一个CreatePersonResponse的实例，与请求对象对应
            CreatePersonResponse resp = client.CreatePerson(req);
            // 输出json格式的字符串回包
            System.out.println(AbstractModel.toJsonString(resp));

            String faceId = resp.getFaceId();
            if (StringUtils.hasText(faceId)) {
                driverInfo.setFaceModelId(faceId);
                driverInfoMapper.updateById(driverInfo);
            }
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
            return false;
            //throw new GuiguException(ResultCodeEnum.AUTHENTICATION_FAILED);
        }
        return true;
    }

    // 获取司机个性化设置信息
    @Override
    public DriverSet getDriverSet(Long driverId) {
        LambdaQueryWrapper<DriverSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverSet::getDriverId, driverId);
        DriverSet driverSet = driverSetMapper.selectOne(wrapper);
        return driverSet;
    }

    // 判断司机当日是否进行过人脸识别
    @Override
    public Boolean isFaceRecognition(Long driverId) {
        // 根据司机id和当日的日期进行查询
        LambdaQueryWrapper<DriverFaceRecognition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverFaceRecognition::getDriverId, driverId);
        // 日期格式：年-月-日
        wrapper.eq(DriverFaceRecognition::getFaceDate, new DateTime().toString("yyyy-MM-dd"));
        Long count = driverFaceRecognitionMapper.selectCount(wrapper);

        return count != 0;
    }

    // 验证司机人脸
    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        // 1.照片比对
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            VerifyFaceRequest req = new VerifyFaceRequest();
            // 设置参数
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(driverFaceModelForm.getDriverId().toString());
            // 返回的resp是一个VerifyFaceResponse的实例，与请求对象对应
            VerifyFaceResponse resp = client.VerifyFace(req);
            // 输出json格式的字符串回包
            System.out.println(AbstractModel.toJsonString(resp));

            // 进行照片比对
            if (resp.getIsMatch()) {
                // 2.如果照片比对成功，进行静态活体检测
                Boolean isSuccess = this.detectLiveFace(driverFaceModelForm.getImageBase64());
                if (isSuccess) {
                    // 3.如果静态活体检测成功，则添加数据到认证表里面
                    DriverFaceRecognition driverFaceRecognition = new DriverFaceRecognition();
                    driverFaceRecognition.setDriverId(driverFaceModelForm.getDriverId());
                    driverFaceRecognition.setFaceDate(new Date());
                    driverFaceRecognitionMapper.insert(driverFaceRecognition);
                    return true;
                }
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }

        throw new GuiguException(ResultCodeEnum.FACE_FAIL);
    }

    // 静态活体检测
    private Boolean detectLiveFace(String imageBase64) {
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DetectLiveFaceRequest req = new DetectLiveFaceRequest();
            // 设置参数
            req.setImage(imageBase64);
            // 返回的resp是一个DetectLiveFaceResponse的实例，与请求对象对应
            DetectLiveFaceResponse resp = client.DetectLiveFace(req);
            // 输出json格式的字符串回包
            System.out.println(DetectLiveFaceResponse.toJsonString(resp));
            // 进行静态活体检测
            if (resp.getIsLiveness()) {
                return true;
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        return false;
    }

    // 更新司机接单状态
    @Override
    public Boolean updateServiceStatus(Long driverId, Integer status) {
        // sql：update driver_set set status = ? where driver_id = ?
        LambdaQueryWrapper<DriverSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverSet::getDriverId, driverId);
        DriverSet driverSet = new DriverSet();
        driverSet.setServiceStatus(status);
        driverSetMapper.update(driverSet, wrapper);
        return true;
    }

    // 获取司机基本信息
    @Override
    public DriverInfoVo getDriverInfo(Long driverId) {
        // 1.根据driverId获取司机基本信息
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        // 2.封装到DriverInfoVo中
        DriverInfoVo driverInfoVo = new DriverInfoVo();
        BeanUtils.copyProperties(driverInfo, driverInfoVo);

        // 计算驾龄
        int firstYear = driverInfo.getDriverLicenseIssueDate().getYear();// 获取驾驶证初次领证年
        int currentYear = new DateTime().getYear();     // 获取当前年
        int driverLicenseAge = currentYear - firstYear;
        driverInfoVo.setDriverLicenseAge(driverLicenseAge);

        return driverInfoVo;
    }
}