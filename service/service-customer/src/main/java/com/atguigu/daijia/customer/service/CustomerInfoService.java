package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CustomerInfoService extends IService<CustomerInfo> {

    Long login(String code);

    CustomerLoginVo getCustomerLoginInfo(Long customerId);

    Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm);

    String getCustomerOpenId(Long customerId);
}
