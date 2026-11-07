package ecommerce.product.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 孤儿文件清理任务的单元测试。
 * <p>
 * 重点保护两件事：
 * <ol>
 *   <li>**静态资源不能被删** —— 前端代码里写死引用的图不在数据库里，删了就是线上事故；</li>
 *   <li>**被引用的文件不能被删** —— 判孤儿要跨全部引用列。</li>
 * </ol>
 * 其中静态资源那条曾经踩过跨平台的坑：判断白名单时用 {@code Path.parts} 解析 URL，
 * Windows 与 Linux 的分段结果不同，会让白名单在本地失效 → 根级静态图被误判成孤儿。
 * 现在统一用 {@code url.split("/")}，用例直接断言这个规则。
 */
class OrphanImageCleanupTaskTest {

    @TempDir
    Path tempDir;

    private OrphanImageCleanupTask task;

    /** 用固定引用集替掉数据库查询，只测文件系统侧的判定逻辑 */
    private static final Set<String> REFERENCED = Set.of(
            "/image/upload/kept.webp",
            "/image/body/kept-in-rich-text.webp");

    @BeforeEach
    void setUp() {
        task = new OrphanImageCleanupTask(null) {
            @Override
            Set<String> loadReferencedPaths() {
                return REFERENCED;
            }
        };
        ReflectionTestUtils.setField(task, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(task, "dryRun", false);
        ReflectionTestUtils.setField(task, "minAgeDays", 7);
        ReflectionTestUtils.setField(task, "maxDelete", 500);
    }

    // ---------- 路径换算 ----------

    @Test
    @DisplayName("磁盘路径换算成对外 URL：opt 派生图与原图同 URL")
    void mapsDiskPathToServedUrl() {
        Path root = tempDir;
        assertEquals("/image/upload/x.webp",
                task.toServedUrl(root, root.resolve("image/upload/x.webp")));
        // opt 是压缩派生图，与原图对外是同一个 URL
        assertEquals("/image/upload/x.webp",
                task.toServedUrl(root, root.resolve("image/opt/upload/x.webp")));
        assertEquals("/image/body/x.webp",
                task.toServedUrl(root, root.resolve("image/opt/body/x.webp")));
        assertEquals("/upload/avatar/13_1.jpg",
                task.toServedUrl(root, root.resolve("avatar/13_1.jpg")));
        // 不在静态托管范围内的文件返回 null
        assertNull(task.toServedUrl(root, root.resolve("random.txt")));
    }

    // ---------- 静态资源白名单 ----------

    @Test
    @DisplayName("前端写死引用的静态资源一律保护")
    void protectsStaticResources() {
        assertTrue(task.isStaticResource("/image/swiper/1.webp"));
        assertTrue(task.isStaticResource("/image/swiper/web-swiper1.png"));
        assertTrue(task.isStaticResource("/image/share/share-3c.jpg"));
        assertTrue(task.isStaticResource("/image/side-bar/side-bar1.webp"));
        assertTrue(task.isStaticResource("/image/QRcode.webp"));
        assertTrue(task.isStaticResource("/image/store-logo.webp"));
        assertTrue(task.isStaticResource("/image/zongjie.webp"));
        assertTrue(task.isStaticResource("/image/price-background.webp"));
    }

    @Test
    @DisplayName("普通上传目录与头像不属于静态资源")
    void doesNotProtectUploadedFiles() {
        assertFalse(task.isStaticResource("/image/upload/1.webp"));
        assertFalse(task.isStaticResource("/image/body/x.webp"));
        assertFalse(task.isStaticResource("/image/category/x.png"));
        assertFalse(task.isStaticResource("/image/banner/x.webp"));
        assertFalse(task.isStaticResource("/upload/avatar/13_1.jpg"));
    }

    // ---------- 端到端清理 ----------

    @Test
    @DisplayName("只删无引用且够老的文件：静态资源、被引用文件、7 天内的新文件都保留")
    void deletesOnlyRealOrphans() throws IOException {
        Path root = tempDir;
        // 被引用：保留
        write(root.resolve("image/upload/kept.webp"), 100);
        // 被富文本引用的正文图：保留
        write(root.resolve("image/body/kept-in-rich-text.webp"), 100);
        // 静态资源：保留（虽然没人引用）
        write(root.resolve("image/swiper/1.webp"), 100);
        write(root.resolve("image/share/share-3c.jpg"), 100);
        write(root.resolve("image/side-bar/side-bar1.webp"), 100);
        write(root.resolve("image/QRcode.webp"), 100);
        write(root.resolve("image/store-logo.webp"), 100);
        write(root.resolve("image/zongjie.webp"), 100);
        write(root.resolve("image/price-background.webp"), 100);
        // 孤儿但刚上传：保留（防「还没写库」的竞态）
        write(root.resolve("image/upload/brand-new.webp"), 0);
        // 真孤儿：够老 + 无引用 → 删
        write(root.resolve("image/after-sale/orphan.webp"), 100);
        write(root.resolve("image/upload/orphan.webp"), 100);
        // opt 派生图：原图是孤儿 → 连带删
        write(root.resolve("image/opt/upload/orphan.webp"), 100);
        // opt 派生图：原图仍被引用 → 保留
        write(root.resolve("image/opt/upload/kept.webp"), 100);
        // 头像目录：未被引用 → 删
        write(root.resolve("avatar/9_1.jpg"), 100);

        task.clean();

        assertTrue(Files.exists(root.resolve("image/upload/kept.webp")));
        assertTrue(Files.exists(root.resolve("image/body/kept-in-rich-text.webp")));
        assertTrue(Files.exists(root.resolve("image/swiper/1.webp")));
        assertTrue(Files.exists(root.resolve("image/share/share-3c.jpg")));
        assertTrue(Files.exists(root.resolve("image/side-bar/side-bar1.webp")));
        assertTrue(Files.exists(root.resolve("image/QRcode.webp")));
        assertTrue(Files.exists(root.resolve("image/store-logo.webp")));
        assertTrue(Files.exists(root.resolve("image/zongjie.webp")));
        assertTrue(Files.exists(root.resolve("image/price-background.webp")));
        assertTrue(Files.exists(root.resolve("image/upload/brand-new.webp")));
        assertTrue(Files.exists(root.resolve("image/opt/upload/kept.webp")));

        assertFalse(Files.exists(root.resolve("image/after-sale/orphan.webp")));
        assertFalse(Files.exists(root.resolve("image/upload/orphan.webp")));
        assertFalse(Files.exists(root.resolve("image/opt/upload/orphan.webp")));
        assertFalse(Files.exists(root.resolve("avatar/9_1.jpg")));
    }

    @Test
    @DisplayName("演练模式只统计不删")
    void dryRunDoesNotDelete() throws IOException {
        Path root = tempDir;
        write(root.resolve("image/upload/orphan.webp"), 100);

        ReflectionTestUtils.setField(task, "dryRun", true);
        task.clean();

        assertTrue(Files.exists(root.resolve("image/upload/orphan.webp")));
    }

    /** 造一个文件并把修改时间往前推 ageDays 天 */
    private void write(Path file, long ageDays) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(ageDays, ChronoUnit.DAYS)));
    }
}
