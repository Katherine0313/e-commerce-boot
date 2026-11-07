package ecommerce.utils;

import ecommerce.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 文件类型魔数校验的单元测试。
 * <p>
 * 重点覆盖「伪装文件」——Content-Type 由客户端提供，可以随便填，
 * 必须靠文件头把它挡下来。
 */
class MediaMagicTest {

    /** PNG 文件头：89 50 4E 47 0D 0A 1A 0A */
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    /** JPEG 文件头：FF D8 FF */
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 0};
    /** GIF 文件头：GIF8 */
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
    /** WEBP 文件头：RIFF + 4 字节长度 + WEBP */
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x1C, (byte) 0xE9, 0, 0, 'W', 'E', 'B', 'P'};
    /** MP4 文件头：第 4~7 字节是 ftyp */
    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

    @Test
    @DisplayName("按文件头识别 png / jpg / gif / webp / mp4")
    void detectsKnownTypes() {
        assertEquals("png", MediaMagic.detectSuffix(PNG));
        assertEquals("jpg", MediaMagic.detectSuffix(JPG));
        assertEquals("gif", MediaMagic.detectSuffix(GIF));
        assertEquals("webp", MediaMagic.detectSuffix(WEBP));
        assertEquals("mp4", MediaMagic.detectSuffix(MP4));
    }

    @Test
    @DisplayName("识别不出的一律返回 null")
    void returnsNullForUnknown() {
        assertNull(MediaMagic.detectSuffix("<?php system($_GET[0]); ?>".getBytes()));
        assertNull(MediaMagic.detectSuffix(new byte[0]));
        assertNull(MediaMagic.detectSuffix(new byte[]{1, 2}));
        assertNull(MediaMagic.detectSuffix(null));
    }

    @Test
    @DisplayName("类型一致时放行")
    void passesWhenTypeMatches() {
        MediaMagic.assertRealType(new MockMultipartFile("file", "a.png", "image/png", PNG), "png");
        MediaMagic.assertRealType(new MockMultipartFile("file", "a.webp", "image/webp", WEBP), "webp");
        MediaMagic.assertRealType(new MockMultipartFile("file", "a.mp4", "video/mp4", MP4), "mp4");
    }

    @Test
    @DisplayName("脚本文件声明成图片一律拒绝")
    void rejectsFakedImage() {
        byte[] script = "<?php system($_GET[0]); ?>".getBytes();
        BusinessException e = assertThrows(BusinessException.class,
                () -> MediaMagic.assertRealType(new MockMultipartFile("file", "x.png", "image/png", script), "png"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
    }

    @Test
    @DisplayName("真实类型与声明类型不一致也拒绝")
    void rejectsMismatchedRealType() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> MediaMagic.assertRealType(new MockMultipartFile("file", "x.png", "image/png", WEBP), "png"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
    }
}
