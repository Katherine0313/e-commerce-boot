package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.entity.Admin;
import ecommerce.auth.mapper.AdminMapper;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.CaptchaVO;
import ecommerce.exception.BusinessException;
import ecommerce.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 图形验证码答案缓存 key：auth:captcha:{captchaId} */
    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    /** 验证码有效期（秒） */
    private static final long CAPTCHA_EXPIRE_SECONDS = 180;
    private static final int CAPTCHA_WIDTH = 120;
    private static final int CAPTCHA_HEIGHT = 40;
    private static final int CAPTCHA_LENGTH = 4;
    /** 去掉 0/O/1/I 等易混淆字符 */
    private static final char[] CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthServiceImpl(AdminMapper adminMapper, JwtUtil jwtUtil,
                           StringRedisTemplate stringRedisTemplate) {
        this.adminMapper = adminMapper;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public AdminLoginVO login(AdminLoginDTO dto) {
        // 1. 参数校验：用户名/密码都不能为空
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        // 2. 图形验证码：一次性使用，校验后立即删除
        validateCaptcha(dto.getCaptchaId(), dto.getCaptchaCode());
        // 3. 按用户名查管理员；查不到或密码不符统一回「用户名或密码错误」，不暴露账号是否存在
        Admin admin = adminMapper.findByUsername(dto.getUsername().trim());
        // ⚠️ 密码目前明文存储、明文比较（admin 表 password 存明文），仅开发期可用。
        //    上线前必须改成 BCrypt：入库时加密、这里改用 matches 校验，否则等于明文裸奔。
        if (admin == null || !dto.getPassword().equals(admin.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // 4. 账号禁用拦截（status=0）
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        // 5. 签发管理员令牌：claims 带上 username/role/id，前端解 payload 做权限判断
        return buildLoginResult(admin);
    }

    @Override
    public CaptchaVO createCaptcha() {
        StringBuilder code = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            code.append(CAPTCHA_CHARS[RANDOM.nextInt(CAPTCHA_CHARS.length)]);
        }
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        try {
            stringRedisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId,
                    code.toString(), Duration.ofSeconds(CAPTCHA_EXPIRE_SECONDS));
        } catch (RuntimeException e) {
            log.error("图形验证码写入 Redis 失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务暂不可用，请稍后重试");
        }
        return CaptchaVO.builder()
                .captchaId(captchaId)
                .image(renderCaptcha(code.toString()))
                .expireIn(CAPTCHA_EXPIRE_SECONDS)
                .build();
    }

    @Override
    public AdminUserVO getInfo(Long adminId) {
        Admin admin = adminMapper.findById(adminId);
        if (admin == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号不存在");
        }
        return AdminUserVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .name(admin.getName())
                .role(admin.getRole())
                .build();
    }

    /** 校验图形验证码：无论对错都删除，防止一张验证码被重复使用 */
    private void validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()
                || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入图形验证码");
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId.trim();
        String saved;
        try {
            saved = stringRedisTemplate.opsForValue().get(key);
            stringRedisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.error("图形验证码读取失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务暂不可用，请稍后重试");
        }
        if (saved == null || !saved.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }
    }

    /** 生成 120x40 的 PNG 验证码图片，返回 data URI */
    private String renderCaptcha(String code) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(247, 249, 252));
            g.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);
            // 干扰线
            for (int i = 0; i < 6; i++) {
                g.setColor(randomGray(170, 225));
                g.drawLine(RANDOM.nextInt(CAPTCHA_WIDTH), RANDOM.nextInt(CAPTCHA_HEIGHT),
                        RANDOM.nextInt(CAPTCHA_WIDTH), RANDOM.nextInt(CAPTCHA_HEIGHT));
            }
            // 验证码字符
            for (int i = 0; i < code.length(); i++) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24 + RANDOM.nextInt(5)));
                g.setColor(randomGray(30, 120));
                g.drawString(String.valueOf(code.charAt(i)), 14 + i * 25, 29 + RANDOM.nextInt(4));
            }
            // 噪点
            for (int i = 0; i < 50; i++) {
                g.setColor(randomGray(160, 230));
                g.fillOval(RANDOM.nextInt(CAPTCHA_WIDTH), RANDOM.nextInt(CAPTCHA_HEIGHT), 1, 1);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            log.error("图形验证码图片生成失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码生成失败，请稍后重试");
        }
    }

    private Color randomGray(int min, int max) {
        int value = min + RANDOM.nextInt(max - min + 1);
        return new Color(value, value, value);
    }

    /** 签发令牌并组装登录响应 */
    private AdminLoginVO buildLoginResult(Admin admin) {
        String token = jwtUtil.generateAdminToken(admin.getId(), admin.getUsername(),
                admin.getRole());
        return AdminLoginVO.builder()
                .token(token)
                .user(AdminUserVO.builder()
                        .id(admin.getId())
                        .username(admin.getUsername())
                        .name(admin.getName())
                        .role(admin.getRole())
                        .build())
                .build();
    }
}
