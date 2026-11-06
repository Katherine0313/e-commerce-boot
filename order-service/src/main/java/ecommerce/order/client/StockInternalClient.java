package ecommerce.order.client;

import ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 调 product-service /internal/stock-write（唯一出入库入口）。
 * <p>
 * 同库跨服务，自动重试 3 次（网络抖动基本 99% 覆盖）。
 * 3 次都失败 → 抛异常，由调用方 catch 处理（订单标记"库存异常待处理"或直接在事务里回滚）。
 */
@Slf4j
@Component
public class StockInternalClient {

    @Value("${stock.internal.url:http://localhost:9003/internal/stock-write}")
    private String stockWriteUrl;

    private final RestTemplate restTemplate;

    public StockInternalClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 写一笔库存变动。
     *
     * @param skuId    SKU id
     * @param skuCode  SKU 编码（产品侧写流水用）
     * @param type     IN / OUT / ADJUST
     * @param reason   销售出库 / 采购入库 / 退货归还 / 盘点调整 / 手工补录
     * @param qty      正数，变动量
     * @param orderItemId 关联订单项 id（可空）
     */
    public void write(Long skuId, String skuCode, String type, String reason, int qty, Long orderItemId) {
        Map<String, Object> body = new HashMap<>();
        body.put("skuId", skuId);
        body.put("skuCode", skuCode);
        body.put("type", type);
        body.put("reason", reason);
        body.put("qty", qty);
        body.put("orderItemId", orderItemId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Call", "true");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        int attempts = 0;
        Exception lastErr = null;
        while (attempts++ < 3) {
            try {
                ResponseEntity<String> resp = restTemplate.postForEntity(stockWriteUrl, request, String.class);
                String body2 = resp.getBody();
                if (body2 != null && body2.contains("\"success\":true")) {
                    lastErr = null;
                    return;
                }
                // HTTP 200 但 success=false，重试
                log.warn("stock-write 返回非成功: {}, attempt={}", body2, attempts);
                lastErr = new RuntimeException("stock-write 非成功响应: " + body2);
            } catch (Exception e) {
                lastErr = e;
                log.warn("stock-write 失败 attempt={}/3, skuId={}, type={}, qty={}: {}",
                    attempts, skuId, type, qty, e.getMessage());
            }
            try { Thread.sleep(500L * attempts); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }
        throw new BusinessException("库存扣减失败（3 次重试仍失败）: skuId=" + skuId + ", type=" + type + ", qty=" + qty
            + ", error=" + (lastErr != null ? lastErr.getMessage() : "unknown"));
    }
}
