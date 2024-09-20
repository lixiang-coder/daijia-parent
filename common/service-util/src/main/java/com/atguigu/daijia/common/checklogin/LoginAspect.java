package com.atguigu.daijia.common.checklogin;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect // 切面类
public class LoginAspect {
    @Resource
    private RedisTemplate redisTemplate;


    //环绕通知进行登录判断
    //切入点表达式：指定对哪些规则的方法进行增强
    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(xzyLogin)")
    public Object login(ProceedingJoinPoint proceedingJoinPoint, XZYLogin xzyLogin) throws Throwable {
        //1 获取request对象
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) attributes;
        HttpServletRequest request = sra.getRequest();

        //2 从请求头获取token
        String token = request.getHeader("token");

        //3 判断token是否为空，如果为空，返回登录提示
        if (StringUtils.isEmpty(token)) {
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }

        //4 token不为空，查询redis
        String customerId = (String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);

        //5 查询redis对应用户id，把用户id放到ThreadLocal里面
        //好处：存放用户id，方面后面好获取，不需要每次都要从redis中获取
        if (StringUtils.hasText(customerId)) {
            AuthContextHolder.setUserId(Long.parseLong(customerId));
        }

        //6 执行业务方法
        return proceedingJoinPoint.proceed();
    }
}
