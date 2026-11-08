package ecommerce.system.service;

import ecommerce.system.support.DockerSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import tools.jackson.databind.JsonNode;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务监控：主机（OSHI）+ JVM（MXBean）+ 容器（Docker API）+ 中间件健康。
 * <p>
 * 口径说明：容器内的 OSHI 读到的是宿主机视角（CPU 核数、内存通常是宿主机），
 * 容器自身实际限额以 Docker stats 为准。
 */
@Slf4j
@Service
public class ServerMonitorService {

    /** 容器 stats 缓存时长：避免每次刷新都把所有容器拉一遍 */
    private static final long CONTAINER_CACHE_MS = 15_000L;

    /** 告警起始时间 key：monitor:alert:since:{type} */
    private static final String ALERT_SINCE_PREFIX = "monitor:alert:since:";

    private final DockerSocketClient dockerSocketClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ServiceMetricsService serviceMetricsService;
    private final String nacosServerAddr;
    private final double cpuThreshold;
    private final double memoryThreshold;
    private final double diskThreshold;

    private final SystemInfo systemInfo = new SystemInfo();
    private volatile long[] lastCpuTicks;
    private volatile List<Map<String, Object>> containerCache = List.of();
    private volatile long containerCacheAt = 0L;

    public ServerMonitorService(DockerSocketClient dockerSocketClient,
                                StringRedisTemplate stringRedisTemplate,
                                ServiceMetricsService serviceMetricsService,
                                @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
                                String nacosServerAddr,
                                @Value("${monitor.alert.cpu-threshold:80}") double cpuThreshold,
                                @Value("${monitor.alert.memory-threshold:85}") double memoryThreshold,
                                @Value("${monitor.alert.disk-threshold:85}") double diskThreshold) {
        this.dockerSocketClient = dockerSocketClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.serviceMetricsService = serviceMetricsService;
        this.nacosServerAddr = nacosServerAddr;
        this.cpuThreshold = cpuThreshold;
        this.memoryThreshold = memoryThreshold;
        this.diskThreshold = diskThreshold;
    }

    public Map<String, Object> overview() {
        Map<String, Object> host = hostInfo();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", host);
        result.put("jvm", jvmInfo());
        result.put("dockerAvailable", dockerSocketClient.isAvailable());
        result.put("containers", containers());
        result.put("services", middlewareStatus());
        result.put("serviceMetrics", serviceMetricsService.list());
        result.put("alerts", alerts(host));
        result.put("thresholds", thresholds());
        return result;
    }

    /** 阈值告警：只返回超过阈值的项；起始时间写 Redis，便于页面展示「持续了多久」 */
    private List<Map<String, Object>> alerts(Map<String, Object> host) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        addAlert(alerts, "cpu", "CPU 使用率", number(host.get("cpuUsagePercent")), cpuThreshold);
        addAlert(alerts, "memory", "内存使用率", number(host.get("memoryUsagePercent")), memoryThreshold);
        Object disks = host.get("disks");
        if (disks instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> disk) {
                    String mount = String.valueOf(disk.get("mount"));
                    addAlert(alerts, "disk:" + mount, "磁盘 " + mount + " 使用率",
                            number(disk.get("usagePercent")), diskThreshold);
                }
            }
        }
        return alerts;
    }

    private void addAlert(List<Map<String, Object>> alerts, String type, String name,
                          double value, double threshold) {
        if (value < threshold) {
            clearAlertSince(type);
            return;
        }
        long since = alertSince(type);
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("type", type);
        alert.put("name", name);
        alert.put("value", value);
        alert.put("threshold", threshold);
        alert.put("level", value >= threshold + 10 ? "critical" : "warning");
        alert.put("since", LocalDateTime.ofInstant(Instant.ofEpochMilli(since), ZoneId.systemDefault()));
        alerts.add(alert);
    }

    private long alertSince(String type) {
        try {
            String value = stringRedisTemplate.opsForValue().get(ALERT_SINCE_PREFIX + type);
            if (value != null && !value.isBlank()) {
                return Long.parseLong(value.trim());
            }
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForValue()
                    .setIfAbsent(ALERT_SINCE_PREFIX + type, String.valueOf(now), Duration.ofDays(1));
            String stored = stringRedisTemplate.opsForValue().get(ALERT_SINCE_PREFIX + type);
            return stored == null || stored.isBlank() ? now : Long.parseLong(stored.trim());
        } catch (RuntimeException e) {
            return System.currentTimeMillis();
        }
    }

    private void clearAlertSince(String type) {
        try {
            stringRedisTemplate.delete(ALERT_SINCE_PREFIX + type);
        } catch (RuntimeException e) {
            // 告警状态清理失败不影响页面返回
        }
    }

    private Map<String, Object> thresholds() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cpu", cpuThreshold);
        result.put("memory", memoryThreshold);
        result.put("disk", diskThreshold);
        return result;
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private Map<String, Object> hostInfo() {
        Map<String, Object> host = new LinkedHashMap<>();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        CentralProcessor processor = hardware.getProcessor();
        GlobalMemory memory = hardware.getMemory();
        OperatingSystem os = systemInfo.getOperatingSystem();

        host.put("hostname", safeHostname());
        host.put("osName", os.getFamily());
        host.put("osVersion", os.getVersionInfo().toString());
        host.put("arch", os.getBitness() + "位");
        host.put("uptimeSeconds", os.getSystemUptime());
        host.put("processCount", os.getProcessCount());
        host.put("cpuCores", processor.getLogicalProcessorCount());
        host.put("cpuModel", processor.getProcessorIdentifier().getName());
        host.put("cpuUsagePercent", round(processor.getSystemCpuLoadBetweenTicks(takeCpuTicks(processor))));
        host.put("cpuDetail", cpuDetail(processor));

        long total = memory.getTotal();
        long available = memory.getAvailable();
        host.put("memoryTotal", total);
        host.put("memoryUsed", total - available);
        host.put("memoryAvailable", available);
        host.put("memoryUsagePercent", percent(total - available, total));
        host.put("swapTotal", memory.getVirtualMemory().getSwapTotal());
        host.put("swapUsed", memory.getVirtualMemory().getSwapUsed());
        host.put("disks", disks(os));
        host.put("network", network(hardware));
        host.put("loadAverage", loadAverage(processor));
        return host;
    }

    /** 保留上一次 ticks，下次请求按区间算 CPU 使用率（第一次调用时返回当前值，结果为 0） */
    private synchronized long[] takeCpuTicks(CentralProcessor processor) {
        long[] previous = lastCpuTicks;
        lastCpuTicks = processor.getSystemCpuLoadTicks();
        return previous == null ? lastCpuTicks : previous;
    }

    private Map<String, Object> cpuDetail(CentralProcessor processor) {
        long[] ticks = processor.getSystemCpuLoadTicks();
        long user = ticks[CentralProcessor.TickType.USER.getIndex()];
        long system = ticks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()];
        long iowait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()];
        long total = 0;
        for (long tick : ticks) {
            total += tick;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userPercent", percent(user, total));
        detail.put("systemPercent", percent(system, total));
        detail.put("idlePercent", percent(idle, total));
        detail.put("iowaitPercent", percent(iowait, total));
        return detail;
    }

    private long[] loadAverage(CentralProcessor processor) {
        double[] load = processor.getSystemLoadAverage(3);
        long[] result = new long[load.length];
        for (int i = 0; i < load.length; i++) {
            result[i] = load[i] < 0 ? -1 : (long) load[i];
        }
        return result;
    }

    private List<Map<String, Object>> disks(OperatingSystem os) {
        List<Map<String, Object>> disks = new ArrayList<>();
        for (OSFileStore store : os.getFileSystem().getFileStores()) {
            long total = store.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long usable = store.getUsableSpace();
            Map<String, Object> disk = new LinkedHashMap<>();
            disk.put("name", store.getName());
            disk.put("mount", store.getMount());
            disk.put("type", store.getType());
            disk.put("total", total);
            disk.put("usable", usable);
            disk.put("usagePercent", percent(total - usable, total));
            disks.add(disk);
        }
        return disks;
    }

    private Map<String, Object> network(HardwareAbstractionLayer hardware) {
        long rx = 0;
        long tx = 0;
        for (NetworkIF networkIF : hardware.getNetworkIFs()) {
            try {
                networkIF.updateAttributes();
                if (networkIF.getBytesRecv() > 0) {
                    rx += networkIF.getBytesRecv();
                }
                if (networkIF.getBytesSent() > 0) {
                    tx += networkIF.getBytesSent();
                }
            } catch (RuntimeException e) {
                log.debug("读取网卡统计失败: {}", e.getMessage());
            }
        }
        Map<String, Object> network = new LinkedHashMap<>();
        network.put("rxBytes", rx);
        network.put("txBytes", tx);
        return network;
    }

    private Map<String, Object> jvmInfo() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) {
                gcCount += gc.getCollectionCount();
            }
            if (gc.getCollectionTime() > 0) {
                gcTime += gc.getCollectionTime();
            }
        }

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsed", heap.getUsed());
        jvm.put("heapMax", heap.getMax());
        jvm.put("heapCommitted", heap.getCommitted());
        jvm.put("heapUsagePercent", percent(heap.getUsed(), heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()));
        jvm.put("nonHeapUsed", nonHeap.getUsed());
        jvm.put("threadCount", threadMXBean.getThreadCount());
        jvm.put("peakThreadCount", threadMXBean.getPeakThreadCount());
        jvm.put("gcCount", gcCount);
        jvm.put("gcTimeMs", gcTime);
        jvm.put("loadedClassCount", classLoadingMXBean.getLoadedClassCount());
        jvm.put("uptimeSeconds", runtimeMXBean.getUptime() / 1000);
        jvm.put("startTime", LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(runtimeMXBean.getStartTime()),
                java.time.ZoneId.systemDefault()));
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("vmName", runtimeMXBean.getVmName());
        // 进程 PID 对排障没价值，也不该暴露给后台页面，直接不返回
        jvm.put("startArgs", runtimeMXBean.getInputArguments().size() + " 项");
        return jvm;
    }

    private List<Map<String, Object>> containers() {
        if (!dockerSocketClient.isAvailable()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        if (now - containerCacheAt < CONTAINER_CACHE_MS) {
            return containerCache;
        }
        List<Map<String, Object>> containers = new ArrayList<>();
        try {
            JsonNode list = dockerSocketClient.listContainers();
            for (JsonNode item : list.values()) {
                Map<String, Object> container = new LinkedHashMap<>();
                container.put("id", item.path("Id").asString(""));
                container.put("name", containerName(item));
                container.put("image", item.path("Image").asString(""));
                container.put("state", item.path("State").asString(""));
                String status = item.path("Status").asString("");
                container.put("status", status);
                container.put("health", healthOf(status, item.path("State").asString("")));
                if ("running".equals(item.path("State").asString(""))) {
                    fillStats(container, item.path("Id").asString(""));
                }
                containers.add(container);
            }
            containerCache = containers;
            containerCacheAt = now;
        } catch (Exception e) {
            log.warn("读取 Docker 容器列表失败: {}", e.getMessage());
            return List.of();
        }
        return containers;
    }

    private String containerName(JsonNode item) {
        JsonNode names = item.path("Names");
        if (names.isArray() && names.size() > 0) {
            return names.get(0).asString("").replaceFirst("/", "");
        }
        return item.path("Id").asString("").length() > 12
                ? item.path("Id").asString("").substring(0, 12)
                : item.path("Id").asString("");
    }

    private String healthOf(String status, String state) {
        if (status.contains("(healthy)")) {
            return "healthy";
        }
        if (status.contains("(unhealthy)")) {
            return "unhealthy";
        }
        return "running".equals(state) ? "up" : state;
    }

    /** 容器 stats：CPU 百分比、内存用量、网络收发 */
    private void fillStats(Map<String, Object> container, String containerId) {
        try {
            JsonNode stats = dockerSocketClient.containerStats(containerId);
            long cpuDelta = stats.path("cpu_stats").path("cpu_usage").path("total_usage").asLong(0)
                    - stats.path("precpu_stats").path("cpu_usage").path("total_usage").asLong(0);
            long systemDelta = stats.path("cpu_stats").path("system_cpu_usage").asLong(0)
                    - stats.path("precpu_stats").path("system_cpu_usage").asLong(0);
            long onlineCpus = stats.path("cpu_stats").path("online_cpus").asLong(0);
            if (onlineCpus <= 0) {
                onlineCpus = stats.path("cpu_stats").path("cpu_usage").path("percpu_usage").size();
            }
            if (onlineCpus <= 0) {
                onlineCpus = 1;
            }
            double cpuPercent = systemDelta > 0 && cpuDelta > 0
                    ? ((double) cpuDelta / systemDelta) * onlineCpus * 100 : 0;
            container.put("cpuPercent", round(cpuPercent));

            long usage = stats.path("memory_stats").path("usage").asLong(0);
            long cache = stats.path("memory_stats").path("stats").path("cache").asLong(0);
            long memoryUsed = Math.max(0, usage - cache);
            long memoryLimit = stats.path("memory_stats").path("limit").asLong(0);
            container.put("memoryUsed", memoryUsed);
            container.put("memoryLimit", memoryLimit);
            container.put("memoryPercent", percent(memoryUsed, memoryLimit));

            long rx = 0;
            long tx = 0;
            JsonNode networks = stats.path("networks");
            for (JsonNode network : networks.values()) {
                rx += network.path("rx_bytes").asLong(0);
                tx += network.path("tx_bytes").asLong(0);
            }
            container.put("networkRx", rx);
            container.put("networkTx", tx);
        } catch (Exception e) {
            log.debug("读取容器 {} stats 失败: {}", containerId, e.getMessage());
            container.put("cpuPercent", 0.0);
            container.put("memoryUsed", 0L);
            container.put("memoryLimit", 0L);
            container.put("memoryPercent", 0.0);
            container.put("networkRx", 0L);
            container.put("networkTx", 0L);
        }
    }

    /** Redis / Nacos 健康状态（容器状态之外的中间件探活） */
    private List<Map<String, Object>> middlewareStatus() {
        List<Map<String, Object>> services = new ArrayList<>();
        services.add(status("redis", redisUp(), "PING"));
        services.add(status("nacos", nacosUp(), nacosServerAddr));
        return services;
    }

    private Map<String, Object> status(String name, boolean up, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("status", up ? "UP" : "DOWN");
        item.put("detail", detail);
        return item;
    }

    private boolean redisUp() {
        try {
            String pong = stringRedisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
            return pong != null && pong.equalsIgnoreCase("PONG");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean nacosUp() {
        String addr = resolveNacosAddress();
        if (addr == null) {
            return false;
        }
        String[] parts = addr.split(":");
        if (parts.length < 2) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1].trim())), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** server-addr 支持 "host1:8848,host2:8848"，取第一个 */
    private String resolveNacosAddress() {
        if (nacosServerAddr == null || nacosServerAddr.isBlank()) {
            return null;
        }
        String first = nacosServerAddr.split(",")[0].trim();
        return first.contains(":") ? first : first + ":8848";
    }

    private String safeHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private double percent(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return round((double) part * 100 / total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
