package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerServiceImpl implements CustomerService {

    // 注入远程调用接口
    @Resource
    private CustomerInfoFeignClient client;

    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public String login(String code) {
        //1 拿着code进行远程调用，返回用户id
        //Result<Long> loginResult = client.login(code);
        // 自定义Feign结果解析，避免了重复校验200和用户id不为空
        Long customerId = client.login(code).getData();

        //2 判断如果返回失败了，返回错误提示
        /*Integer codeResult = loginResult.getCode();
        if (codeResult != 200) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //3 获取远程调用返回用户id
        Long customerId = loginResult.getData();

        //4 判断返回用户id是否为空，如果为空，返回错误提示
        if (customerId == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }*/

        //5 生成token字符串
        String token = UUID.randomUUID().toString().replaceAll("-", "");

        //6 把用户id放到Redis，设置过期时间
        // key:token  value:customerId
        //redisTemplate.opsForValue().set(token,customerId.toString(),30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token,
                customerId.toString(),
                RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                TimeUnit.SECONDS);

        //7 返回token
        return token;
    }

    //获取客户登录的信息
    @Override
    public CustomerLoginVo getCustomerLoginInfo(Long customerId) {
        //1 根据token查询redis
        //String customerId = (String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);

        //2 查询token在redis中对应的用户id
        /*if (!StringUtils.hasText(customerId)) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }*/

        //3 根据用户id进行远程调用 得到用户信息
        //Result<CustomerLoginVo> customerLoginVoResult = client.getCustomerLoginInfo(customerId);
        // 自定义Feign结果解析，避免了重复校验200和用户id不为空
        CustomerLoginVo customerLoginVo = client.getCustomerLoginInfo(customerId).getData();

        //4 返回用户信息
        /*if (customerLoginVoResult.getCode() != 200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        CustomerLoginVo customerLoginVo = customerLoginVoResult.getData();
        if (customerLoginVo == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }*/

        return customerLoginVo;
    }

    //更新客户微信手机号码
    @Override
    public Object updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm) {
        // 注：微信公众号个人版不能获取手机号，直接跳过
        Result<Boolean> booleanResult = client.updateWxPhoneNumber(updateWxPhoneForm);
        return true;
    }
}
