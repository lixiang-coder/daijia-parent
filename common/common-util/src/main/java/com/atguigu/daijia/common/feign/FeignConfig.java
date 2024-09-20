package com.atguigu.daijia.common.feign;

import feign.codec.Decoder;
import feign.optionals.OptionalDecoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign配置类
 */
@Configuration
public class FeignConfig {

    /**
     * 自定义解析器
     *
     * @param msgConverters 信息转换
     * @param customizers   自定义参数
     * @return 解析器
     */
    @Bean
    public Decoder decoder(ObjectFactory<HttpMessageConverters> msgConverters, ObjectProvider<HttpMessageConverterCustomizer> customizers) {
        return new OptionalDecoder((new ResponseEntityDecoder(new FeignCustomDataDecoder(new SpringDecoder(msgConverters, customizers)))));
    }

}