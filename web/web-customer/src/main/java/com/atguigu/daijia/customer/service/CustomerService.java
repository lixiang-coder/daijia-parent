package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;

public interface CustomerService {

    String login(String code);

    CustomerLoginVo getCustomerLoginInfo(Long customerId);
}
