package com.atguigu.daijia.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMaProperties {
    /**
     * 小程序微信公众平台appId
     */
    private String appId;

    /**
     * 小程序微信公众平台api秘钥
     */
    private String secret;
}