package com.atguigu.daijia.map.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.repository.OrderServiceLocationRepository;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.entity.map.OrderServiceLocation;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;

    @Resource
    private OrderServiceLocationRepository orderServiceLocationRepository;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;


    // 司机开启接单，更新司机位置信息
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        // 把司机位置信息添加redis里面geo
        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue(), updateDriverLocationForm.getLatitude().doubleValue());
        // 添加到redis中
        redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION, point, updateDriverLocationForm.getDriverId().toString());
        return true;
    }

    // 司机关闭接单，删除司机位置信息
    @Override
    public Boolean removeDriverLocation(Long driverId) {
        // 将司机的位置信息从redis中删除
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        return true;
    }

    // 搜索附近满足条件的司机
    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {
        //搜索经纬度位置5公里以内的司机
        //1 操作redis里面geo
        //创建point，经纬度位置
        Point point = new Point(searchNearByDriverForm.getLongitude().doubleValue(), searchNearByDriverForm.getLatitude().doubleValue());

        //定义距离，5公里
        Distance distance = new Distance(SystemConstant.NEARBY_DRIVER_RADIUS, RedisGeoCommands.DistanceUnit.KILOMETERS);

        //创建circle对象，point  distance
        Circle circle = new Circle(point, distance);

        //定义GEO参数，设置返回结果包含内容
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance()  //包含距离
                .includeCoordinates() //包含坐标
                .sortAscending(); //升序

        GeoResults<RedisGeoCommands.GeoLocation<String>> result = redisTemplate.opsForGeo().radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);

        //2 查询redis最终返回list集合
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = result.getContent();

        //3 对查询list集合进行处理
        // 遍历list集合，得到每个司机信息
        // 根据每个司机个性化设置信息判断
        List<NearByDriverVo> list = new ArrayList<>();
        if (!CollectionUtils.isEmpty(content)) {
            Iterator<GeoResult<RedisGeoCommands.GeoLocation<String>>> iterator = content.iterator();
            while (iterator.hasNext()) {
                GeoResult<RedisGeoCommands.GeoLocation<String>> item = iterator.next();

                //获取司机id
                Long driverId = Long.parseLong(item.getContent().getName());

                //远程调用，根据司机id个性化设置信息
                Result<DriverSet> driverSetResult = driverInfoFeignClient.getDriverSet(driverId);
                DriverSet driverSet = driverSetResult.getData();

                //判断订单里程order_distance
                BigDecimal orderDistance = driverSet.getOrderDistance();
                //orderDistance==0，司机没有限制的
                //如果不等于0 ，比如30，接单30公里代驾订单。
                //接单距离 - 当前单子距离  < 0,不复合条件
                // 30          35
                if (orderDistance.doubleValue() != 0 && orderDistance.subtract(searchNearByDriverForm.getMileageDistance()).doubleValue() < 0) {
                    continue;
                }

                //判断接单里程 accept_distance
                //当前接单距离
                BigDecimal currentDistance = new BigDecimal(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);

                BigDecimal acceptDistance = driverSet.getAcceptDistance();
                if (acceptDistance.doubleValue() != 0 && acceptDistance.subtract(currentDistance).doubleValue() < 0) {
                    continue;
                }

                //封装复合条件数据
                NearByDriverVo nearByDriverVo = new NearByDriverVo();
                nearByDriverVo.setDriverId(driverId);
                nearByDriverVo.setDistance(currentDistance);
                list.add(nearByDriverVo);
            }
        }
        return list;
    }

    // 司机赶往代驾起始点：更新订单地址到缓存
    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        OrderLocationVo orderLocationVo = new OrderLocationVo();
        // 设置参数：经纬度
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());

        String key = RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId();
        redisTemplate.opsForValue().set(key, orderLocationVo);
        return true;
    }

    // 司机赶往代驾起始点：从缓存中获取订单经纬度位置
    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        String key = RedisConstant.UPDATE_ORDER_LOCATION + orderId;
        OrderLocationVo orderLocationVo = (OrderLocationVo) redisTemplate.opsForValue().get(key);
        return orderLocationVo;
    }

    // 开始代驾服务：保存代驾服务订单位置（MongoRepository操作MongoDB）
    @Override
    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        List<OrderServiceLocation> list = new ArrayList<>();

        // 遍历 orderLocationServiceFormList 集合，得到每一个对象
        orderLocationServiceFormList.forEach(orderServiceLocationForm -> {
            // 将 OrderServiceLocationForm ---> OrderServiceLocation
            OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
            BeanUtils.copyProperties(orderServiceLocationForm, orderServiceLocation);

            // 补充剩余参数
            orderServiceLocation.setId(ObjectId.get().toString());
            orderServiceLocation.setCreateTime(new Date());

            // 一个一个保存，性能比较低
            //orderServiceLocationRepository.save(orderServiceLocation);
            list.add(orderServiceLocation);
        });
        // 批量添加到MongoDB
        orderServiceLocationRepository.saveAll(list);
        return true;
    }

    // 代驾服务：获取订单服务最后一个位置信息（MongoTemplate操作MongoDB）
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        /* 查询MongoDB
                1.查询条件 ：orderId
                2.根据创建时间降序排列
                3.获取最新一条数据
        */
        Query query = new Query();
        query.addCriteria(Criteria.where("orderId").is(orderId));
        query.with(Sort.by(Sort.Order.desc("createTime")));
        query.limit(1);

        OrderServiceLocation orderServiceLocation = mongoTemplate.findOne(query, OrderServiceLocation.class);
        OrderServiceLastLocationVo orderServiceLastLocationVo = new OrderServiceLastLocationVo();
        BeanUtils.copyProperties(orderServiceLocation, orderServiceLastLocationVo);
        return orderServiceLastLocationVo;
    }

    // 代驾服务：计算订单实际里程
    @Override
    public BigDecimal calculateOrderRealDistance(Long orderId) {
        //1 根据订单id获取代驾订单位置信息，根据创建时间排序（升序）。在批量批量保存订单位置信息到MongoDB中查询

        //第一种方式
        //OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
        //orderServiceLocation.setOrderId(orderId);
        //Example<OrderServiceLocation> example = Example.of(orderServiceLocation);
        //Sort sort = Sort.by(Sort.Direction.ASC, "createTime");
        //List<OrderServiceLocation> list = orderServiceLocationRepository.findAll(example, sort);

        //第二种方式
        //MongoRepository只需要 按照规则 在MongoRepository把查询方法创建出来就可以了
        // 总体规则：
        //1 查询方法名称 以 get  |  find  | read开头
        //2 后面查询字段名称，满足驼峰式命名，比如OrderId
        //3 字段查询条件添加关键字，比如Like  OrderBy   Asc
        // 具体编写 ： 根据订单id获取代驾订单位置信息，根据创建时间排序（升序）
        List<OrderServiceLocation> list = orderServiceLocationRepository.findByOrderIdOrderByCreateTimeAsc(orderId);

        //2 第一步查询返回订单位置信息list集合
        //把list集合遍历，得到每个位置信息，计算两个位置距离
        //把计算所有距离相加操作
        double realDistance = 0;

        if (!CollectionUtils.isEmpty(list)) {
            for (int i = 0, size = list.size() - 1; i < size; i++) {
                OrderServiceLocation location1 = list.get(i);
                OrderServiceLocation location2 = list.get(i + 1);

                //计算位置距离
                double distance = LocationUtil.getDistance(location1.getLatitude().doubleValue(),
                        location1.getLongitude().doubleValue(),
                        location2.getLatitude().doubleValue(),
                        location2.getLongitude().doubleValue());

                realDistance += distance;
            }
        }

        // todo 测试过程中，不可能拿着电脑跑，实际代驾GPS位置没有变化，模拟：实际代驾里程 = 预期里程 + 5
        if(realDistance == 0) {
            return orderInfoFeignClient.getOrderInfo(orderId).getData().getExpectDistance().add(new BigDecimal("5"));
        }

        //3 返回最终计算实际距离
        return new BigDecimal(realDistance);
    }
}
