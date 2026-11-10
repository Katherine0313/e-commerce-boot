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
import java.util.List;
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

    /**
     * 订单发货生成出库单的地址。不配置时由 {@link #stockWriteUrl} 自动推导，
     * 保证本地 / Docker（STOCK_INTERNAL_URL 已指向 product-service）都不用新增环境变量。
     */
    @Value("${stock.internal.shipment-url:}")
    private String shipmentUrlOverride;

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

        post(stockWriteUrl, body,
            "stock-write skuId=" + skuId + ", type=" + type + ", qty=" + qty,
            "库存扣减失败（3 次重试仍失败）");
    }

    /**
     * 订单发货 → 让 product-service 生成一张销售出库单（只落单据，不重复扣库存）。
     * <p>
     * 库存在下单时已经扣减过；relatedNo = 订单号，product-service 侧按它做幂等。
     *
     * @param orderNo      订单号（出库单的关联单号）
     * @param operatorId   操作人 id
     * @param operatorName 操作人名称
     * @param items        明细：skuId / skuCode / skuName / qty
     */
    public void createShipmentOrder(String orderNo, Long operatorId, String operatorName,
                                    List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SHIPMENT");
        body.put("bizType", "ORDER_SHIP");
        body.put("relatedNo", orderNo);
        body.put("remark", "订单发货自动生成");
        body.put("operatorId", operatorId);
        body.put("operatorName", operatorName);
        body.put("items", items);

        post(shipmentUrl(), body, "stock-orders/shipment orderNo=" + orderNo,
            "生成销售出库单失败（3 次重试仍失败）");
    }

    /** 发货出库单地址：优先取显式配置，否则从 stock-write 地址推导同主机的内部路径 */
    private String shipmentUrl() {
        if (shipmentUrlOverride != null && !shipmentUrlOverride.isBlank()) {
            return shipmentUrlOverride.trim();
        }
        return stockWriteUrl.replaceAll("/stock-write$", "/stock-orders/shipment");
    }

    /** POST + 重试 3 次；HTTP 200 但 success=false 也重试，最终失败抛 BusinessException */
    private void post(String url, Map<String, Object> body, String bizDesc, String failPrefix) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Call", "true");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        int attempts = 0;
        Exception lastErr = null;
        while (attempts++ < 3) {
            try {
                ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
                String respBody = resp.getBody();
                if (respBody != null && respBody.contains("\"success\":true")) {
                    return;
                }
                log.warn("{} 返回非成功: {}, attempt={}", bizDesc, respBody, attempts);
                lastErr = new RuntimeException("非成功响应: " + respBody);
            } catch (Exception e) {
                lastErr = e;
                log.warn("{} 失败 attempt={}/3: {}", bizDesc, attempts, e.getMessage());
            }
            try { Thread.sleep(500L * attempts); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }
        throw new BusinessException(failPrefix + ": " + bizDesc
            + ", error=" + (lastErr != null ? lastErr.getMessage() : "unknown"));
    }
}
