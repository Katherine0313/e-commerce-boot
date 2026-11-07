package ecommerce.product.service;

import ecommerce.task.MonitoredTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 孤儿上传文件清理：删掉磁盘上「数据库里已经没有任何引用」的上传图片/视频。
 * <p>
 * <b>为什么需要</b>：上传接口只管写盘，不管回收。运营换图、删商品、删 Banner 之后，
 * 老文件一直躺在磁盘上，商品图目录尤其明显（本地实测：498 个文件里 21 个已成孤儿）。
 * 商品被物理删除后 {@code order_items.image} 等快照列仍会引用同一个文件，
 * 所以「有没有引用」必须看<b>全部引用列的并集</b>，不能只看 {@code products.image}。
 * <p>
 * <b>引用列清单（新增图片类型时必须补一条）</b>：见 {@link #REFERENCED_SQL}。
 * 漏一条的后果是「有引用的图被误删」，所以改代码前先想清楚新类型落在哪张表。
 * <p>
 * <b>软删数据算「仍被引用」</b>：软删行可以恢复，删掉它的图会导致恢复后裂图。
 * 宁可多留文件（占点空间），也不能删掉活数据（不可逆）。
 * <p>
 * <b>静态资源白名单</b>：{@code /image/swiper/}、{@code /image/share/}、{@code /image/side-bar/}
 * 和 {@code /image/} 根下的 4 张散图是前端代码里<b>写死</b>引用的，不在任何表里，
 * 扫描时必须排除，否则站点素材会被删光。
 * <p>
 * <b>安全兜底</b>：默认演练模式（只统计不删）、7 天门槛（防「刚上传还没写库」的竞态）、
 * 单次删除上限、静态白名单。演练输出看没问题再把 dry-run 关掉。
 */
@Slf4j
@Component
public class OrphanImageCleanupTask {

    /**
     * 所有存放上传文件路径的列（单值 / JSON 数组 / 富文本 HTML 混在一起）。
     * <p>
     * 用一条 UNION ALL 一次取完，再用正则统一提取路径 —— 不为每种格式各写一套解析：
     * 单值是路径本身，JSON 数组是 {@code ["..."]}，JSON 对象数组是 {@code [{"url":"..."}]}，
     * 富文本是 {@code <img src="...">}，正则都能覆盖。
     * <p>
     * <b>新增图片类型时必须在这里补一条</b>，否则新类型的图会被误判为孤儿。
     * <p>
     * 富文本两列用 LIKE 过滤，避免把整表 longtext 拉进内存。
     */
    private static final String REFERENCED_SQL = """
            SELECT image FROM after_sale_items
            UNION ALL SELECT images FROM after_sales
            UNION ALL SELECT image FROM cart
            UNION ALL SELECT image FROM categories
            UNION ALL SELECT logo FROM delivery_express
            UNION ALL SELECT images FROM feedback
            UNION ALL SELECT image FROM order_items
            UNION ALL SELECT product_image FROM product_reviews
            UNION ALL SELECT images FROM product_reviews
            UNION ALL SELECT image FROM product_sku
            UNION ALL SELECT image FROM products
            UNION ALL SELECT body FROM products WHERE body LIKE '%/image/%'
            UNION ALL SELECT cover FROM promotion_article
            UNION ALL SELECT content FROM promotion_article WHERE content LIKE '%/image/%'
            UNION ALL SELECT pic_url FROM promotion_article_category
            UNION ALL SELECT image FROM promotion_banner
            UNION ALL SELECT images FROM promotion_banner
            UNION ALL SELECT avatar FROM `user`
            """;

    /**
     * 从任意文本里提取本地图片路径。
     * <p>
     * 兼容四种存储形态：单值路径、JSON 字符串数组、JSON 对象数组、富文本 HTML。
     * 左边界用 lookbehind 挡住「外链 URL 里恰好含 /image/」的情况
     * （{@code user.avatar} 可能存微信头像这类绝对地址）。
     */
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "(?:(?<=^)|(?<=[\"'(\\s=]))((?:/image/[A-Za-z0-9_.\\-/]+|/upload/avatar/[A-Za-z0-9_.\\-]+))");

    /** 上传根目录（相对运行目录），与 WebMvcConfig 的静态映射一致 */
    @Value("${upload.orphan-cleanup.dir:upload}")
    private String uploadDir;

    /** 图片根目录（相对 upload/） */
    private static final String IMAGE_DIR = "image";

    /** 头像根目录（相对 upload/） */
    private static final String AVATAR_DIR = "avatar";

    /**
     * 前端代码写死引用的静态目录（URL 第 3 段）。
     * <p>
     * 出处：{@code ecommerce-mall/src/views/layouts/DefaultLayout.vue}、{@code views/products/Paid.vue}、
     * {@code components/StoreCard.vue}；{@code e-commerce/src/pages/goods/list/Paid.vue}；
     * 小程序 {@code components/custom-swiper.vue}。
     */
    private static final Set<String> STATIC_DIRS = Set.of("swiper", "share", "side-bar");

    /** 前端代码写死引用的根级散图（文件名，不含目录） */
    private static final Set<String> STATIC_FILES = Set.of(
            "QRcode.webp", "price-background.webp", "store-logo.webp", "zongjie.webp");

    /** 单次最多删多少个文件，防止逻辑有误时一次删光 */
    @Value("${upload.orphan-cleanup.max-delete:500}")
    private int maxDelete;

    /** 只统计不删（true = 演练）。上线先演练，看清单没问题再改 false */
    @Value("${upload.orphan-cleanup.dry-run:true}")
    private boolean dryRun;

    /** 文件至少要有多少天没动过才允许删（防「刚上传还没写库」的竞态） */
    @Value("${upload.orphan-cleanup.min-age-days:7}")
    private int minAgeDays;

    private final JdbcTemplate jdbcTemplate;

    public OrphanImageCleanupTask(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行一次清理。
     * <p>
     * 任何一步失败都直接抛出、什么都不删：引用集合没查全就删文件 = 误删活数据。
     */
    @MonitoredTask(name = "orphanImageCleanup", description = "清理磁盘上已无数据库引用的上传图片", cron = "0 30 4 * * ?", allowManualRun = true)
    public void clean() {
        Path uploadRoot = Paths.get(uploadDir);
        if (!Files.isDirectory(uploadRoot)) {
            log.warn("上传目录不存在，跳过孤儿清理: {}", uploadRoot.toAbsolutePath());
            return;
        }
        // 第一步：查出所有仍被引用的路径。查失败就整个任务放弃，绝不带着不完整的引用集去删文件
        Set<String> referenced = loadReferencedPaths();

        // 第二步：扫磁盘，找出没有引用的文件
        List<Path> orphans = findOrphans(uploadRoot, referenced);

        if (orphans.isEmpty()) {
            log.info("孤儿文件清理完成: 没有需要清理的文件（引用路径 {} 条）", referenced.size());
            return;
        }
        // 第三步：演练模式只打印清单不删；否则按上限真删
        if (dryRun) {
            log.info("孤儿文件清理【演练模式，未删除任何文件】: 待清理 {} 个 -> {}",
                    orphans.size(), brief(orphans));
            return;
        }
        int deleted = 0;
        for (Path file : orphans) {
            if (deleted >= maxDelete) {
                log.warn("孤儿文件清理达到单次上限 {}，剩余 {} 个留到下次处理", maxDelete, orphans.size() - deleted);
                break;
            }
            try {
                Files.deleteIfExists(file);
                deleted++;
            } catch (IOException e) {
                log.warn("删除孤儿文件失败: {}, {}", file, e.getMessage());
            }
        }
        log.info("孤儿文件清理完成: 待清理 {} 个，实际删除 {} 个", orphans.size(), deleted);
    }

    /** 日志里只打前若干条，避免一次几千个孤儿把日志刷爆 */
    private String brief(List<Path> files) {
        int limit = 100;
        return files.size() <= limit ? files.toString()
                : files.subList(0, limit) + " ...（共 " + files.size() + " 条）";
    }

    /**
     * 查询所有仍被引用的上传路径。
     * <p>
     * 查不到就抛异常 —— 引用集不完整比不清危险得多。
     * <p>
     * 包级可见是为了让单元测试能替换掉数据库查询，只测文件系统侧的判定逻辑。
     */
    Set<String> loadReferencedPaths() {
        Set<String> paths = new LinkedHashSet<>();
        List<String> values = jdbcTemplate.query(REFERENCED_SQL, (rs, rowNum) -> rs.getString(1));
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            Matcher matcher = PATH_PATTERN.matcher(value);
            while (matcher.find()) {
                paths.add(matcher.group(1));
            }
        }
        log.info("孤儿文件清理: 数据库中被引用的路径 {} 条", paths.size());
        return paths;
    }

    /**
     * 扫描上传目录，挑出「没被引用、且不在静态白名单里、且够老」的文件。
     * <p>
     * 每个文件都换算成它对外提供的 URL 再判断：
     * {@code upload/image/upload/x.webp} → {@code /image/upload/x.webp}，
     * {@code upload/image/opt/upload/x.webp} → {@code /image/upload/x.webp}（opt 是压缩派生图，
     * 与原图同 URL，原图被删时它跟着一起清），{@code upload/avatar/x.jpg} → {@code /upload/avatar/x.jpg}。
     */
    private List<Path> findOrphans(Path uploadRoot, Set<String> referenced) {
        List<Path> orphans = new ArrayList<>();
        long minAgeMillis = minAgeDays * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        try (Stream<Path> stream = Files.walk(uploadRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String url = toServedUrl(uploadRoot, file);
                if (url == null) {
                    continue;
                }
                if (referenced.contains(url) || isStaticResource(url)) {
                    continue;
                }
                try {
                    if (now - Files.getLastModifiedTime(file).toMillis() < minAgeMillis) {
                        // 太新：可能刚上传还没写库，下一轮再看
                        continue;
                    }
                } catch (IOException e) {
                    log.debug("读取文件修改时间失败，跳过: {}", file);
                    continue;
                }
                orphans.add(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("扫描上传目录失败", e);
        }
        return orphans;
    }

    /**
     * 换算文件对外提供的 URL；不是静态托管的文件返回 null。
     * <p>
     * 注意用 {@code relativize} + {@code toString().replace('\\','/')} 拆路径，
     * 不能用 {@code Path.getNameCount()} 之类的分段 API —— Windows 与 Linux 的分隔符不同，
     * 分段结果会不一致，判断会失准。
     * <p>
     * 包级可见是为了让单元测试直接断言路径换算规则。
     */
    String toServedUrl(Path uploadRoot, Path file) {
        String relative = uploadRoot.relativize(file).toString().replace('\\', '/');
        if (relative.startsWith(IMAGE_DIR + "/")) {
            // upload/image/xxx → /image/xxx；upload/image/opt/xxx → /image/xxx（opt 与原图同 URL）
            String rest = relative.substring(IMAGE_DIR.length() + 1);
            if (rest.startsWith("opt/")) {
                rest = rest.substring("opt/".length());
            }
            return "/image/" + rest;
        }
        if (relative.startsWith(AVATAR_DIR + "/")) {
            return "/upload/" + relative;
        }
        return null;
    }

    /**
     * 判断是不是前端写死引用的静态资源。
     * <p>
     * 用 URL 字符串按 "/" 拆（与平台无关），不用 {@code Path.parts} ——
     * Windows 与 Linux 的路径分段结果不同，用 {@code Path} 会让白名单在本地失效。
     * <p>
     * 包级可见是为了让单元测试直接断言白名单规则。
     */
    boolean isStaticResource(String url) {
        String[] segments = url.split("/");
        // /image/swiper/1.webp → ["", "image", "swiper", "1.webp"]
        if (segments.length >= 4 && STATIC_DIRS.contains(segments[2])) {
            return true;
        }
        // /image/QRcode.webp → ["", "image", "QRcode.webp"]
        if (segments.length == 3 && STATIC_FILES.contains(segments[2])) {
            return true;
        }
        return false;
    }
}
