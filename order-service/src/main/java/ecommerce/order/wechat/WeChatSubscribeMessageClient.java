package ecommerce.order.wechat;

import ecommerce.order.config.WeChatSubscribeProperties;
import ecommerce.utils.HttpClientUtil;
import ecommerce.utils.WeChatAccessTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信一次性订阅消息发送客户端。
 * <p>
 * 目前只有「订单未支付提醒」一个模板，字段固定：
 * thing1=商品名称、character_string5=订单编号、amount7=订单金额、time8=订单失效时间。
 * <p>
 * access_token 统一由 {@link WeChatAccessTokenProvider} 提供（Redis 共享），
 * 失效时清缓存并重试一次。
 */
@Slf4j
@Component
public class WeChatSubscribeMessageClient {

    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** thing 类型最多 20 个字符 */
    private static final int THING_MAX_LENGTH = 20;

    private final WeChatAccessTokenProvider accessTokenProvider;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;
    private final WeChatSubscribeProperties properties;

    public WeChatSubscribeMessageClient(WeChatAccessTokenProvider accessTokenProvider,
                                        HttpClientUtil httpClientUtil,
                                        ObjectMapper objectMapper,
                                        WeChatSubscribeProperties properties) {
        this.accessTokenProvider = accessTokenProvider;
        this.httpClientUtil = httpClientUtil;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 发送结果：决定定时任务是否把它标记为「已推送」 */
    public enum SendResult {
        /** 发送成功 */
        SUCCESS,
        /** 永久失败（用户拒收/无配额、字段非法、openid 无效等），重试也没用 */
        PERMANENT_FAIL,
        /** 临时失败（token、网络等），下一轮可以重试 */
        RETRY
    }

    /**
     * 发送未支付提醒。
     *
     * @param openid      用户 openid
     * @param productName 商品名称（过长会截断到 20 字）
     * @param orderNo     订单编号
     * @param amount      订单金额
     * @param expireAt    订单失效时间
     */
    public SendResult sendUnpaidReminder(String openid, String productName, String orderNo,
                                         BigDecimal amount, LocalDateTime expireAt) {
        if (properties.getTemplateId() == null || properties.getTemplateId().isBlank()) {
            log.warn("未配置 wechat.subscribe.template-id，跳过未支付提醒");
            return SendResult.PERMANENT_FAIL;
        }

        // 消息点击跳转：pages/order/pending-pay?orderNo=xxx
        String page = properties.getPage() + "?orderNo=" + orderNo;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", value(truncateThing(productName)));
        data.put("character_string5", value(orderNo));
        data.put("amount7", value(amount == null ? "0.00"
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        data.put("time8", value(expireAt == null ? "" : expireAt.format(TIME_FMT)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", properties.getTemplateId());
        body.put("page", page);
        body.put("miniprogram_state", properties.getMiniprogramState());
        body.put("lang", "zh_CN");
        body.put("data", data);

        try {
            String json = objectMapper.writeValueAsString(body);
            String token = accessTokenProvider.getAccessToken();
            JsonNode result = doSend(token, json);

            int errcode = result.path("errcode").asInt(-1);
            // token 失效：清缓存，重取后重试一次
            if (errcode == 40001 || errcode == 42001) {
                log.warn("access_token 失效，刷新后重试: orderNo={}", orderNo);
                accessTokenProvider.invalidate(token);
                result = doSend(accessTokenProvider.getAccessToken(), json);
                errcode = result.path("errcode").asInt(-1);
            }

            if (errcode == 0) {
                log.info("未支付提醒发送成功: orderNo={}", orderNo);
                return SendResult.SUCCESS;
            }

            String errmsg = result.path("errmsg").asText("");
            // 43101 用户拒收/无配额；47003 字段值非法；40003 openid 无效；40037 模板 ID 无效
            boolean permanent = errcode == 43101 || errcode == 47003
                    || errcode == 40003 || errcode == 40037;
            log.warn("未支付提醒发送失败: orderNo={}, errcode={}, errmsg={}, permanent={}",
                    orderNo, errcode, errmsg, permanent);
            return permanent ? SendResult.PERMANENT_FAIL : SendResult.RETRY;
        } catch (Exception e) {
            // 网络异常、Redis/微信接口异常：下一轮再试
            log.warn("未支付提醒发送异常: orderNo={}", orderNo, e);
            return SendResult.RETRY;
        }
    }

    private JsonNode doSend(String accessToken, String json) {
        String respBody = httpClientUtil.doPostJson(SEND_URL + "?access_token=" + accessToken, json);
        return objectMapper.readTree(respBody);
    }

    private Map<String, String> value(String value) {
        return Map.of("value", value == null ? "" : value);
    }

    /** thing 类型最多 20 个字符，按码点截断，避免中文被截成半个 */
    private String truncateThing(String value) {
        if (value == null) {
            return "";
        }
        int length = value.codePointCount(0, value.length());
        if (length <= THING_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, THING_MAX_LENGTH - 1)) + "…";
    }
}
