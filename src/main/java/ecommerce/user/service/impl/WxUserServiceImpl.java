package ecommerce.user.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.properties.WeChatProperties;
import ecommerce.user.entity.WxUser;
import ecommerce.user.mapper.WxUserMapper;
import ecommerce.user.service.WxUserService;
import ecommerce.user.vo.WxUserLoginVO;
import ecommerce.utils.HttpClientUtil;
import ecommerce.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 小程序用户业务实现
 */
@Slf4j
@Service
public class WxUserServiceImpl implements WxUserService {

    private final WxUserMapper wxUserMapper;
    private final WeChatProperties weChatProperties;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final HttpClientUtil httpClientUtil;

    public WxUserServiceImpl(WxUserMapper wxUserMapper,
                             WeChatProperties weChatProperties,
                             JwtUtil jwtUtil,
                             ObjectMapper objectMapper,
                             HttpClientUtil httpClientUtil) {
        this.wxUserMapper = wxUserMapper;
        this.weChatProperties = weChatProperties;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.httpClientUtil = httpClientUtil;
    }

    @Override
    public WxUserLoginVO login(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少 code");
        }
        String openid = getOpenid(code);
        WxUser user = findOrRegister(openid);
        String token = jwtUtil.generateWxUserToken(user.getId(), user.getOpenid());
        return WxUserLoginVO.builder()
                .token(token)
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .build();
    }

    /**
     * 调微信 jscode2session，用 code 换 openid
     */
    private String getOpenid(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + weChatProperties.getAppid()
                + "&secret=" + weChatProperties.getSecret()
                + "&js_code=" + code
                + "&grant_type=authorization_code";

        String respBody;
        try {
            respBody = httpClientUtil.get(url);
        } catch (Exception e) {
            // 异常信息里可能带完整 URL（含 secret），只记服务端日志，绝不回传前端
            log.error("调用微信 jscode2session 失败", e);
            throw new BusinessException("微信登录服务暂时不可用，请稍后重试");
        }

        Map<String, Object> wxResp;
        try {
            wxResp = objectMapper.readValue(respBody, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("解析微信响应失败, body={}", respBody, e);
            throw new BusinessException("微信登录服务返回异常");
        }

        // 微信成功时不带 errcode，失败时才有；errcode/errmsg 不含密钥，可以给用户看
        if (wxResp.containsKey("errcode")) {
            log.warn("微信返回错误: {}", wxResp);
            throw new BusinessException("微信登录失败: " + wxResp.get("errmsg"));
        }

        Object openid = wxResp.get("openid");
        if (openid == null || openid.toString().isBlank()) {
            // 注意：不能把 wxResp 整个打出来，里面含 session_key
            log.error("微信响应里没有 openid, 字段={}", wxResp.keySet());
            throw new BusinessException("微信登录失败：未获取到用户标识");
        }
        return openid.toString();
    }

    /**
     * 按 openid 找用户，找不到就注册一个
     */
    private WxUser findOrRegister(String openid) {
        WxUser user = wxUserMapper.findByOpenid(openid);
        if (user != null) {
            return user;
        }
        WxUser newUser = WxUser.builder().openid(openid).build();
        try {
            wxUserMapper.insertWxUser(newUser);
            return newUser;
        } catch (DuplicateKeyException e) {
            // 并发场景：另一个请求抢先注册了同一个 openid，回查即可
            return wxUserMapper.findByOpenid(openid);
        }
    }
}
