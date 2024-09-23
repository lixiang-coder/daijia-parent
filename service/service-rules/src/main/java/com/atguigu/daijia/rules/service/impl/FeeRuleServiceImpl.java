package com.atguigu.daijia.rules.service.impl;

import com.atguigu.daijia.model.form.rules.FeeRuleRequest;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponse;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.rules.service.FeeRuleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class FeeRuleServiceImpl implements FeeRuleService {
    @Resource
    private KieContainer kieContainer;


    // 计算订单费用
    @Override
    public FeeRuleResponseVo calculateOrderFee(FeeRuleRequestForm calculateOrderFeeForm) {
        // 1.封装到输入对象 FeeRuleRequest
        FeeRuleRequest feeRuleRequest = new FeeRuleRequest();
        feeRuleRequest.setDistance(calculateOrderFeeForm.getDistance());
        feeRuleRequest.setWaitMinute(calculateOrderFeeForm.getWaitMinute());
        // 将Date时间格式转为  小时:分钟:秒 并且是字符串的形式
        Date startTime = calculateOrderFeeForm.getStartTime();
        feeRuleRequest.setStartTime(new DateTime(startTime).toString("HH:mm:ss"));


        // 2.调用规则中心 Drools
        // 开启会话
        KieSession kieSession = kieContainer.newKieSession();
        FeeRuleResponse feeRuleResponse = new FeeRuleResponse();
        // 设置全局变量
        kieSession.setGlobal("feeRuleResponse",feeRuleResponse);
        // 设置订单对象
        kieSession.insert(feeRuleRequest);
        // 触发规则
        kieSession.fireAllRules();
        // 中止会话
        kieSession.dispose();

        // 3.将全局变量的值封装返回数据到 FeeRuleResponseVo
        FeeRuleResponseVo feeRuleResponseVo = new FeeRuleResponseVo();
        BeanUtils.copyProperties(feeRuleResponse,feeRuleResponseVo);

        return feeRuleResponseVo;
    }
}
