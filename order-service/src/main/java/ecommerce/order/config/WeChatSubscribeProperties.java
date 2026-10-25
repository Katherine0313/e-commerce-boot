package ecommerce.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信一次性订阅消息配置（订单未支付提醒）。
 */
@Data
@ConfigurationProperties(prefix = "wechat.subscribe")
public class WeChatSubscribeProperties {

    /** 模板 ID */
    private String templateId;

    /** 点击消息跳转的小程序页面（不带前导斜杠），订单号由客户端拼到 query 上 */
    private String page = "pages/order/pending-pay";

    /** 环境：正式版 formal / 体验版 trial / 开发版 developer */
    private String miniprogramState = "formal";

    /** 提前多少分钟推送：订单 30 分钟失效，默认提前 10 分钟（即下单后第 20 分钟） */
    private int notifyBeforeMinutes = 10;
}
