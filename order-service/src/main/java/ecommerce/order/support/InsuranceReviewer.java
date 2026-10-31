package ecommerce.order.support;

import ecommerce.common.insurance.InsuranceOptionVO;
import ecommerce.common.insurance.InsurancePolicy;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 增值保障复核工具（order-service 侧）。
 * <p>
 * 加购/下单时前端会带上保障明细和费用，这里一律不采信：
 * 只保留 {@link InsurancePolicy} 允许该商品购买的保障项，费用按规则重算，
 * 明细也按后端的 code/name/fee 重新序列化，避免前端伪造保障项或改金额。
 */
public final class InsuranceReviewer {

    private InsuranceReviewer() {
    }

    /**
     * 复核前端传来的保障明细（加购弹窗传的数组）。
     *
     * @return 复核后的明细（code/name/fee）与单件费用合计
     */
    public static InsurancePolicy.Result review(Long categoryId, BigDecimal price,
                                               List<Map<String, Object>> detail, ObjectMapper objectMapper) {
        return InsurancePolicy.review(categoryId, price, codesOf(detail));
    }

    /**
     * 复核已序列化的保障明细（购物车/下单请求里的 JSON 字符串）。
     *
     * @return 复核后的明细（code/name/fee）与单件费用合计
     */
    public static InsurancePolicy.Result review(Long categoryId, BigDecimal price,
                                               String detailJson, ObjectMapper objectMapper) {
        return InsurancePolicy.review(categoryId, price, codesOfJson(detailJson, objectMapper));
    }

    /** 明细数组 → 保障项编码集合（取不到 code 的直接忽略） */
    public static Set<String> codesOf(List<Map<String, Object>> detail) {
        Set<String> codes = new LinkedHashSet<>();
        if (detail == null) {
            return codes;
        }
        for (Map<String, Object> item : detail) {
            if (item == null) {
                continue;
            }
            Object code = item.get("code");
            if (code != null && !String.valueOf(code).isBlank()) {
                codes.add(String.valueOf(code));
            }
        }
        return codes;
    }

    /** JSON 字符串 → 保障项编码集合，解析失败返回空集合 */
    @SuppressWarnings("unchecked")
    public static Set<String> codesOfJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                return codesOf((List<Map<String, Object>>) list);
            }
        } catch (Exception e) {
            // 明细解析失败按「没买保障」处理
        }
        return Set.of();
    }

    /** 复核后的明细 → JSON 字符串，空或序列化失败返回 "[]" */
    public static String toJson(List<InsuranceOptionVO> options, ObjectMapper objectMapper) {
        if (options == null || options.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "[]";
        }
    }
}
