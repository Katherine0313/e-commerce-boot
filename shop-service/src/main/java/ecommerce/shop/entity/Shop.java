package ecommerce.shop.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 店铺实体（对应 shops 表，多租户主体）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shop {
    private Long id;
    private String name;
    /** 是否模板店：1 是 0 否（模板店永不过期，expires_at 为 NULL） */
    private Integer isTemplate;
    private LocalDateTime createTime;
    /** 过期时间（沙箱店 24 小时；模板店 NULL） */
    private LocalDateTime expiresAt;
    /** 访客标识（前端 localStorage 的 UUID；仅沙箱店使用，模板店/历史店为 NULL） */
    private String visitorKey;
}
