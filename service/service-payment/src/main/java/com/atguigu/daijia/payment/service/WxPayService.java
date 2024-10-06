package com.atguigu.daijia.payment.service;

import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;
import jakarta.servlet.http.HttpServletRequest;

public interface WxPayService {

    WxPrepayVo createWxPayment(PaymentInfoForm paymentInfoForm);

    Object queryPayStatus(String orderNo);

    void wxnotify(HttpServletRequest request);
}
