package ecommerce.utils;

import ecommerce.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传文件的真实类型校验（魔数 / magic number）。
 * <p>
 * <b>为什么需要</b>：{@code MultipartFile.getContentType()} 是客户端自己填的，可以随意伪造 ——
 * 把一个 .jsp 内容标成 {@code image/png} 就能骗过后端的白名单校验并落盘。
 * 魔数是文件自身开头的固定字节序列，伪造不了。
 * <p>
 * <b>为什么不用 ImageIO</b>：JDK 的 ImageIO 不支持 webp，而本项目的图片统一输出 webp
 * （见后台 {@code utils/image-spec.js}），用 ImageIO 校验会把所有正常图片判为非法。
 * 所以只读文件头做类型判定，不做解码。
 * <p>
 * 支持的类型与后端白名单保持一致：png / jpg / gif / webp / mp4。
 */
public final class MediaMagic {

    /** 需要读取的文件头长度：webp 要看第 8~11 字节的 "WEBP"，mp4 要看第 4~7 字节的 "ftyp" */
    private static final int HEAD_LENGTH = 12;

    private MediaMagic() {
    }

    /**
     * 读取文件头判定真实类型。
     *
     * @param head 文件头字节（至少 12 字节，不足则按实际长度判定）
     * @return 规范后缀 png / jpg / gif / webp / mp4；识别不出返回 null
     */
    public static String detectSuffix(byte[] head) {
        if (head == null || head.length < 3) {
            return null;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return "png";
        }
        // JPEG: FF D8 FF
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // GIF: "GIF8"
        if (head.length >= 4 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return "gif";
        }
        // WEBP: "RIFF" + 4 字节长度 + "WEBP"
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        // MP4: 第 4~7 字节是 "ftyp"（ISO BMFF 的第一个 box）
        if (head.length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            return "mp4";
        }
        return null;
    }

    /**
     * 校验文件真实类型与声明类型是否一致，不一致直接抛 400。
     * <p>
     * 调用方先按 Content-Type 白名单拿到期望后缀，再调这里做二次确认。
     *
     * @param file           上传的文件
     * @param expectedSuffix 期望的后缀（png/jpg/gif/webp/mp4）
     * @throws BusinessException 文件类型不符或读取文件头失败
     */
    public static void assertRealType(MultipartFile file, String expectedSuffix) {
        String actual = detectSuffix(readHead(file));
        if (actual == null || !actual.equals(expectedSuffix)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "文件内容与声明类型不符，请重新选择文件");
        }
    }

    /** 读取文件头；读取失败按类型不符处理，不把 IO 异常抛给上层 */
    private static byte[] readHead(MultipartFile file) {
        byte[] head = new byte[HEAD_LENGTH];
        try (InputStream in = file.getInputStream()) {
            // readNBytes 返回实际读到的字节数，读不满的部分保持 0，判定时靠长度检查兜住
            in.readNBytes(head, 0, HEAD_LENGTH);
            return head;
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件读取失败，请重新上传");
        }
    }
}