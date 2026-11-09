package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.entity.Admin;
import ecommerce.auth.entity.AdminMenu;
import ecommerce.auth.entity.AdminRole;
import ecommerce.auth.enums.LoginResult;
import ecommerce.auth.enums.LoginType;
import ecommerce.auth.mapper.AdminMapper;
import ecommerce.auth.mapper.RbacMapper;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.service.LoginLogService;
import ecommerce.auth.support.PasswordUtil;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.CaptchaVO;
import ecommerce.auth.vo.MenuVO;
import ecommerce.exception.BusinessException;
import ecommerce.security.AdminOnlineSessionStore;
import ecommerce.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 图形验证码答案缓存 key：auth:captcha:{captchaId} */
    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    /** 登录失败次数 key：auth:login:fail:{username}:{ip} */
    private static final String LOGIN_FAIL_KEY_PREFIX = "auth:login:fail:";
    /** 验证码尺寸 */
    private static final int CAPTCHA_WIDTH = 120;
    private static final int CAPTCHA_HEIGHT = 40;
    private static final int CAPTCHA_LENGTH = 4;
    /** 去掉 0/O/1/I 等易混淆字符 */
    private static final char[] CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminMapper adminMapper;
    private final RbacMapper rbacMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final LoginLogService loginLogService;
    private final AdminOnlineSessionStore onlineSessionStore;

    /** 连续密码错误多少次后锁定 */
    @Value("${auth.login.max-fail-count:5}")
    private int maxFailCount;
    /** 锁定分钟数 */
    @Value("${auth.login.lock-minutes:10}")
    private long lockMinutes;

    public AuthServiceImpl(AdminMapper adminMapper, RbacMapper rbacMapper, JwtUtil jwtUtil,
                           StringRedisTemplate stringRedisTemplate,
                           LoginLogService loginLogService,
                           AdminOnlineSessionStore onlineSessionStore) {
        this.adminMapper = adminMapper;
        this.rbacMapper = rbacMapper;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.loginLogService = loginLogService;
        this.onlineSessionStore = onlineSessionStore;
    }

    @Override
    public AdminLoginVO login(AdminLoginDTO dto, String clientIp, String userAgent) {
        // 1. 参数校验：用户名/密码都不能为空
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            recordLoginLog(null, dto == null ? null : dto.getUsername(),
                    LoginResult.PARAM_MISSING, "用户名或密码为空", clientIp, userAgent);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        String username = dto.getUsername().trim();
        // 2. 失败次数锁定：按「用户名 + IP」，避免被别人恶意锁死账号
        try {
            assertNotLocked(username, clientIp);
        } catch (BusinessException e) {
            recordLoginLog(null, username, LoginResult.LOCKED, e.getMessage(), clientIp, userAgent);
            throw e;
        }
        // 3. 图形验证码：一次性使用，校验后立即删除
        try {
            validateCaptcha(dto.getCaptchaId(), dto.getCaptchaCode());
        } catch (BusinessException e) {
            recordLoginLog(null, username, LoginResult.CAPTCHA_ERROR, e.getMessage(), clientIp, userAgent);
            throw e;
        }
        // 4. 按用户名查管理员；查不到或密码不符统一回「用户名或密码错误」，不暴露账号是否存在
        Admin admin = adminMapper.findByUsername(username);
        // BCrypt 密文走 matches，历史明文账号首次登录成功后自动升级为 BCrypt
        if (admin == null || !PasswordUtil.matches(dto.getPassword(), admin.getPassword())) {
            long failures = recordLoginFailure(username, clientIp);
            boolean locked = failures >= maxFailCount;
            // 本次是错误密码：达到上限时记为「触发锁定」，否则普通账密错误
            recordLoginLog(admin == null ? null : admin.getId(), username,
                    locked ? LoginResult.LOCKED : LoginResult.BAD_CREDENTIALS,
                    locked ? lockedMessage(null) : "用户名或密码错误", clientIp, userAgent);
            if (locked) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, lockedMessage(null));
            }
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // 5. 登录成功清零失败计数
        clearLoginFailures(username, clientIp);
        // 6. 账号停用拦截（status=0；已软删的账号 findByUsername 查不到，走不到这里）
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            recordLoginLog(admin.getId(), username, LoginResult.USER_DISABLED,
                    "账号已停用", clientIp, userAgent);
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已停用");
        }
        // 历史明文密码升级为 BCrypt（失败不影响本次登录）
        if (!PasswordUtil.isEncoded(admin.getPassword())) {
            adminMapper.updatePassword(admin.getId(), PasswordUtil.encode(dto.getPassword()),
                    LocalDateTime.now());
        }
        // 7. 签发管理员令牌：claims 带上 username/role/id，前端解 payload 做权限判断
        AdminLoginVO result = buildLoginResult(admin, clientIp, userAgent);
        recordLoginLog(admin.getId(), admin.getUsername(), LoginResult.SUCCESS,
                null, clientIp, userAgent);
        return result;
    }

    /**
     * 登出。
     * <p>
     * /api/logout 在免登录清单里（JwtInterceptor 不处理），拿不到 UserContext，
     * 所以这里自己解一次 Authorization 头：解析失败（没带/过期/伪造）就不记，
     * 避免给没有身份信息的请求刷一堆空账号日志。
     */
    @Override
    public void logout(String authorization, String clientIp, String userAgent) {
        Long adminId = null;
        String username = null;
        String token = bearerToken(authorization);
        String sid = null;
        if (token != null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                username = claims.get("username", String.class);
                sid = claims.get("sid", String.class);
                Object id = claims.get("id");
                adminId = id instanceof Number number ? number.longValue() : null;
            } catch (Exception e) {
                log.debug("登出时解析令牌失败，跳过登录日志: {}", e.getMessage());
            }
        }
        // 删除在线会话（老 token 没有 sid，跳过）
        if (adminId != null && sid != null) {
            onlineSessionStore.remove(adminId, sid);
        }
        if (adminId == null && (username == null || username.isBlank())) {
            return;
        }
        recordLoginLog(adminId, username, LoginType.LOGOUT, LoginResult.SUCCESS,
                null, clientIp, userAgent);
    }

    /** 从 Authorization 头取 Bearer 令牌；格式不对返回 null */
    private String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        return value.startsWith("Bearer ") ? value.substring(7).trim() : null;
    }

    /** 记一条登录日志；默认 LOGIN，异常已在 LoginLogService 内部兜底 */
    private void recordLoginLog(Long adminId, String username, LoginResult result,
                                String reason, String clientIp, String userAgent) {
        recordLoginLog(adminId, username, LoginType.LOGIN, result, reason, clientIp, userAgent);
    }

    private void recordLoginLog(Long adminId, String username, LoginType type, LoginResult result,
                                String reason, String clientIp, String userAgent) {
        loginLogService.record(adminId, username, type, result, reason, clientIp, userAgent);
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
                    code.toString());
        } catch (RuntimeException e) {
            log.error("图形验证码写入 Redis 失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务暂不可用，请稍后重试");
        }
        return CaptchaVO.builder()
                .captchaId(captchaId)
                .image(renderCaptcha(code.toString()))
                .build();
    }

    @Override
    public AdminUserVO getInfo(Long adminId) {
        Admin admin = adminMapper.findById(adminId);
        if (admin == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号不存在");
        }
        return buildUserVO(admin, loadAuthInfo(admin));
    }

    /** 已锁定则直接拒绝，提示剩余分钟数 */
    private void assertNotLocked(String username, String clientIp) {
        String key = loginFailKey(username, clientIp);
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return;
            }
            long failures = Long.parseLong(value);
            if (failures < maxFailCount) {
                return;
            }
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    lockedMessage(ttl == null || ttl <= 0 ? null : ttl));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // Redis 异常时不做锁定拦截，避免因为缓存故障导致管理员完全无法登录
            log.warn("读取登录失败次数失败，跳过锁定检查: {}", e.getMessage());
        }
    }

    /** 记录一次密码错误，返回累计失败次数；Redis 异常时返回 0 */
    private long recordLoginFailure(String username, String clientIp) {
        String key = loginFailKey(username, clientIp);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return 0;
            }
            // 第一次计数时设置窗口；达到上限时重置为完整锁定时长
            if (count == 1 || count >= maxFailCount) {
                stringRedisTemplate.expire(key, Duration.ofMinutes(lockMinutes));
            }
            return count;
        } catch (RuntimeException e) {
            log.warn("记录登录失败次数失败，跳过锁定: {}", e.getMessage());
            return 0;
        }
    }

    /** 登录成功清零失败计数 */
    private void clearLoginFailures(String username, String clientIp) {
        try {
            stringRedisTemplate.delete(loginFailKey(username, clientIp));
        } catch (RuntimeException e) {
            log.warn("清除登录失败次数失败，忽略: {}", e.getMessage());
        }
    }

    /** 失败计数 key：用户名小写 + IP，避免攻击者用错误密码把账号全局锁死 */
    private String loginFailKey(String username, String clientIp) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        return LOGIN_FAIL_KEY_PREFIX + name + ":" + ip;
    }

    private String lockedMessage(Long ttlSeconds) {
        long minutes = lockMinutes;
        if (ttlSeconds != null && ttlSeconds > 0) {
            minutes = Math.max(1, (ttlSeconds + 59) / 60);
        }
        return "尝试次数过多，请 " + minutes + " 分钟后再试";
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
    private AdminLoginVO buildLoginResult(Admin admin, String clientIp, String userAgent) {
        AuthInfo auth = loadAuthInfo(admin);
        // 会话标识：写入 JWT claim + Redis 会话，后台「在线用户」按它定位
        String sid = UUID.randomUUID().toString().replace("-", "");
        String token = jwtUtil.generateAdminToken(admin.getId(), admin.getUsername(),
                auth.roles(), auth.permissions(), sid);
        onlineSessionStore.register(admin.getId(), sid, admin.getUsername(),
                clientIp, userAgent, auth.roles());
        return AdminLoginVO.builder()
                .token(token)
                .user(buildUserVO(admin, auth))
                .build();
    }

    /** 读取管理员的角色、权限、菜单；超级管理员直接放行并返回全部菜单 */
    private AuthInfo loadAuthInfo(Admin admin) {
        List<AdminRole> roleRows = rbacMapper.listRolesByAdminId(admin.getId());
        List<String> roleKeys = roleRows.stream()
                .map(AdminRole::getRoleKey)
                .filter(Objects::nonNull)
                .toList();
        // 超级管理员：绑定了 admin 角色，或 id=1 的根账号（兜底，防止绑定丢失导致无人能管理系统）
        boolean superAdmin = roleKeys.contains("admin") || Objects.equals(admin.getId(), 1L);
        if (superAdmin) {
            // 超管的 roles 必定带 admin 标识
            List<String> roles = roleKeys.contains("admin") ? roleKeys : List.of("admin");
            return new AuthInfo(roles, List.of("*:*:*"),
                    buildMenuTree(rbacMapper.listAllMenus()));
        }
        return new AuthInfo(roleKeys,
                rbacMapper.listPermsByAdminId(admin.getId()),
                buildMenuTree(rbacMapper.listMenusByAdminId(admin.getId())));
    }

    /** 扁平菜单组装成树；按钮（F）已在 SQL 中排除 */
    private List<MenuVO> buildMenuTree(List<AdminMenu> menus) {
        Map<Long, MenuVO> nodes = new LinkedHashMap<>();
        for (AdminMenu menu : menus) {
            nodes.put(menu.getId(), MenuVO.builder()
                    .id(menu.getId())
                    .parentId(menu.getParentId())
                    .name(menu.getMenuName())
                    .type(menu.getMenuType())
                    .path(menu.getPath())
                    .perms(menu.getPerms())
                    .icon(menu.getIcon())
                    .sortOrder(menu.getSortOrder())
                    .children(new ArrayList<>())
                    .build());
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : nodes.values()) {
            MenuVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        Comparator<MenuVO> bySort = Comparator.comparing(MenuVO::getSortOrder,
                Comparator.nullsLast(Integer::compareTo));
        roots.sort(bySort);
        roots.forEach(root -> root.getChildren().sort(bySort));
        return roots;
    }

    private AdminUserVO buildUserVO(Admin admin, AuthInfo auth) {
        return AdminUserVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .name(admin.getName())
                .adminAvatar(admin.getAdminAvatar())
                .roles(auth.roles())
                .permissions(auth.permissions())
                .menus(auth.menus())
                .build();
    }

    /** 一次登录需要的 RBAC 快照 */
    private record AuthInfo(List<String> roles, List<String> permissions, List<MenuVO> menus) {
    }
}