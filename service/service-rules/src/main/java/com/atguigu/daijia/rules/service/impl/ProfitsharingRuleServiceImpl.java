package com.atguigu.daijia.rules.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.model.entity.rule.ProfitsharingRule;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequest;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponse;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.rules.mapper.ProfitsharingRuleMapper;
import com.atguigu.daijia.rules.service.ProfitsharingRuleService;
import com.atguigu.daijia.rules.utils.DroolsHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class ProfitsharingRuleServiceImpl implements ProfitsharingRuleService {

    @Autowired
    private ProfitsharingRuleMapper rewardRuleMapper;


    // 计算系统分账费用
    @Override
    public ProfitsharingRuleResponseVo calculateOrderProfitsharingFee(ProfitsharingRuleRequestForm profitsharingRuleRequestForm) {
        //封装传入对象
        ProfitsharingRuleRequest profitsharingRuleRequest = new ProfitsharingRuleRequest();
        profitsharingRuleRequest.setOrderAmount(profitsharingRuleRequestForm.getOrderAmount());
        profitsharingRuleRequest.setOrderNum(profitsharingRuleRequestForm.getOrderNum());
        log.info("传入参数：{}", JSON.toJSONString(profitsharingRuleRequest));

        //获取最新订单费用规则
        ProfitsharingRule profitsharingRule = rewardRuleMapper.selectOne(new LambdaQueryWrapper<ProfitsharingRule>().orderByDesc(ProfitsharingRule::getId).last("limit 1"));
        KieSession kieSession = DroolsHelper.loadForRule(profitsharingRule.getRule());

        //封装返回对象
        ProfitsharingRuleResponse profitsharingRuleResponse = new ProfitsharingRuleResponse();
        kieSession.setGlobal("profitsharingRuleResponse", profitsharingRuleResponse);
        // 设置订单对象
        kieSession.insert(profitsharingRuleRequest);
        // 触发规则
        kieSession.fireAllRules();
        // 中止会话
        kieSession.dispose();
        log.info("计算结果：{}", JSON.toJSONString(profitsharingRuleResponse));

        //封装返回对象
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = new ProfitsharingRuleResponseVo();
        profitsharingRuleResponseVo.setProfitsharingRuleId(profitsharingRule.getId());
        BeanUtils.copyProperties(profitsharingRuleResponse, profitsharingRuleResponseVo);
        return profitsharingRuleResponseVo;
    }
}
