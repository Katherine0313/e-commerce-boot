package ecommerce.order.mapper;

import ecommerce.order.entity.Promotion;
import ecommerce.order.entity.PromotionItem;
import ecommerce.order.entity.PromotionRule;
import ecommerce.order.entity.PromotionUsage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销 Mapper（order-service 读活动配置 + 写使用记录）。
 * <p>
 * 与 product-service 共用同一个 MySQL 库，下单时直接读 promotion 配置；
 * 时间条件由 Java 传入 now（服务器时区 UTC，不使用 NOW()）。
 */
public interface PromotionMapper {

    // 当前生效的活动（未删除、未提前结束、now 在时间窗内）
    List<Promotion> listActive(@Param("now") LocalDateTime now);

    List<PromotionItem> listItemsByPromotionIds(@Param("promotionIds") List<Long> promotionIds);

    List<PromotionRule> listRulesByPromotionIds(@Param("promotionIds") List<Long> promotionIds);

    // 批量写促销使用记录
    int insertUsages(@Param("usages") List<PromotionUsage> usages);
}
