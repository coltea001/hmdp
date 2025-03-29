package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合则返回错误信息
            return Result.fail("手机号格式错误");
        }

        //3.符合则生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到session
        stringRedisTemplate.opsForValue().set("login:code:" + phone , code ,5, TimeUnit.MINUTES);

        //5.发送验证码
        log.debug("验证码发送成功：{}",code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        String cacheCode = (String) stringRedisTemplate.opsForValue().get("login:code:" + phone);
        String code = loginForm.getCode();
        if(code==null||!code.equals(cacheCode)){
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", phone).one();

        if(user == null){
            user = creatUserWithPhone(phone);
        }

        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        userMap.put("id",String.valueOf(user.getId()));
        stringRedisTemplate.opsForHash().putAll("login:token:" + token, userMap);
        stringRedisTemplate.expire("login:token:" + token,7,TimeUnit.DAYS);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM:"));
        String key = "sign:" + keySuffix + userId;
        stringRedisTemplate.opsForValue().setBit(key, now.getDayOfMonth()-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM:"));
        String key = "sign:" + keySuffix + userId;
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth())).valueAt(0));
        if(result == null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        int count = 0;
        while(num > 0){
            if((num & 1) == 1){
                count++;
            }
            else{
                break;
            }
            num >>= 1;
        }

        return Result.ok(count);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
