package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        String typeListJson = stringRedisTemplate.opsForValue().get("cache:typeList");

        if(StrUtil.isNotBlank(typeListJson)){
            List<ShopType> typeList = JSONUtil.toList(typeListJson,ShopType.class);
            return Result.ok(typeList);
        }

        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();

        stringRedisTemplate.opsForValue().set("cache:typeList",JSONUtil.toJsonStr(typeList),1, TimeUnit.DAYS);

        return Result.ok(typeList);
    }
}
