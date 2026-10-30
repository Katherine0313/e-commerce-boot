package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 促销活动（order-service 只读 promotion 表，用于下单算价）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {
    private Long id;
    private String name;
    private String type;
    private String ruleType;
    private String targetGroup;
    private String scopeType;
    private String tagName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
