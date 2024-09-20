package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;

public interface CustomerService {

    String login(String code);

    CustomerLoginVo getCustomerLoginInfo(Long customerId);

    Object updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm);
}
