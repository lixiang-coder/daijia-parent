package com.atguigu.daijia.common.feign;


import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;

import java.io.IOException;
import java.lang.reflect.Type;


/**
 * OpenFeign 自定义结果解码器
 */
public class FeignCustomDataDecoder implements Decoder {
    private final SpringDecoder decoder;

    public FeignCustomDataDecoder(SpringDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Object object = this.decoder.decode(response, type);
        if (null == object) {
            throw new DecodeException(ResultCodeEnum.FEIGN_FAIL.getCode(), ResultCodeEnum.FEIGN_FAIL.getMessage(), response.request());//"数据解析失败"
        }
        if(object instanceof Result<?>) {
            Result<?> result = ( Result<?>)object;
            //返回状态!=200，直接抛出异常，全局异常捕获异常，接口提示
            if (result.getCode().intValue() != ResultCodeEnum.SUCCESS.getCode().intValue()) {
                throw new DecodeException(result.getCode(), result.getMessage(), response.request());//"数据解析失败"
            }
            //远程调用必须有返回值，具体调用中不用判断result.getData() == null，这里统一处理
            if (null == result.getData()) {
                throw new DecodeException(ResultCodeEnum.FEIGN_FAIL.getCode(), ResultCodeEnum.FEIGN_FAIL.getMessage(), response.request());//"数据解析失败"
            }
            return result;
        }
        return object;
    }
}