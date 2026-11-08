package ecommerce.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务服务 Actuator 指标聚合。
 * <p>
 * 各服务只暴露 health/info/metrics，网关不转发 /actuator/**；system-service 通过 Nacos
 * 找到实例后在内网直连，聚合 JVM 堆、CPU、线程与 HTTP 接口耗时。
 * <p>
 * 结果缓存 30 秒，避免页面 5 秒轮询把下游服务的 actuator 打爆。
 */
@Slf4j
@Service
public class ServiceMetricsService {

    private static final long CACHE_MS = 30_000L;

    /** 监控目标：8 个 Java 服务（网关 + 6 业务 + 本服务） */
    private static final List<String> SERVICES = List.of(
            "gateway", "auth-service", "product-service", "order-service",
            "aftersale-service", "user-service", "file-service", "system-service");

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile List<Map<String, Object>> cache = List.of();
    private volatile long cacheAt = 0L;

    public ServiceMetricsService(DiscoveryClient discoveryClient,
                                 RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list() {
        long now = System.currentTimeMillis();
        if (now - cacheAt < CACHE_MS) {
            return cache;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String service : SERVICES) {
            result.add(collect(service));
        }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("name"))));
        cache = result;
        cacheAt = now;
        return result;
    }

    private Map<String, Object> collect(String service) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", service);
        ServiceInstance instance = firstInstance(service);
        if (instance == null) {
            item.put("status", "DOWN");
            item.put("message", "Nacos 未找到实例");
            return item;
        }
        String baseUrl = "http://" + instance.getHost() + ":" + instance.getPort();
        item.put("instance", instance.getHost() + ":" + instance.getPort());
        item.put("status", health(baseUrl));
        try {
            JsonNode heapUsed = metric(baseUrl, "jvm.memory.used", "area:heap");
            JsonNode heapMax = metric(baseUrl, "jvm.memory.max", "area:heap");
            JsonNode cpu = metric(baseUrl, "process.cpu.usage", null);
            JsonNode threads = metric(baseUrl, "jvm.threads.live", null);
            JsonNode gc = metric(baseUrl, "jvm.gc.pause", null);
            JsonNode http = metric(baseUrl, "http.server.requests", null);

            long used = measurement(heapUsed, "VALUE");
            long max = measurement(heapMax, "VALUE");
            item.put("heapUsed", used);
            item.put("heapMax", max);
            item.put("heapUsagePercent", max > 0 ? round(used * 100.0 / max) : 0);
            item.put("cpuUsagePercent", round(measurement(cpu, "VALUE") * 100));
            item.put("threadCount", measurement(threads, "VALUE"));
            item.put("gcCount", measurement(gc, "COUNT"));
            item.put("gcTimeMs", measurement(gc, "TOTAL_TIME"));
            long httpCount = measurement(http, "COUNT");
            long httpTotalTime = measurement(http, "TOTAL_TIME");
            item.put("httpCount", httpCount);
            item.put("httpTotalTimeMs", round(httpTotalTime * 1000));
            item.put("httpAvgMs", httpCount > 0 ? round(httpTotalTime * 1000 / httpCount) : 0);
            item.put("httpMaxMs", round(measurement(http, "MAX") * 1000));
        } catch (Exception e) {
            item.put("message", shorten(e.getMessage()));
        }
        return item;
    }

    private ServiceInstance firstInstance(String service) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            return instances == null || instances.isEmpty() ? null : instances.get(0);
        } catch (RuntimeException e) {
            log.debug("查询 Nacos 实例失败, service={}: {}", service, e.getMessage());
            return null;
        }
    }

    /**
     * 用 liveness 而不是整体 health：RabbitMQ 等可选依赖未接入时整体 health 会是 DOWN，
     * 但服务进程本身可用，页面不该误判。
     */
    private String health(String baseUrl) {
        try {
            String body = restClient.get().uri(baseUrl + "/actuator/health/liveness")
                    .retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body);
            String status = node.path("status").asString("");
            return status.isBlank() ? "UNKNOWN" : status;
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private JsonNode metric(String baseUrl, String name, String tag) throws Exception {
        String uri = baseUrl + "/actuator/metrics/" + name + (tag == null ? "" : "?tag=" + tag);
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return objectMapper.readTree(body);
    }

    /** 从 measurements 数组里按 statistic 取 value */
    private long measurement(JsonNode metric, String statistic) {
        if (metric == null) {
            return 0;
        }
        JsonNode measurements = metric.path("measurements");
        for (JsonNode item : measurements.values()) {
            if (statistic.equals(item.path("statistic").asString(""))) {
                return Math.round(item.path("value").asDouble(0));
            }
        }
        return 0;
    }

    private String shorten(String message) {
        if (message == null) {
            return "指标采集失败";
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
