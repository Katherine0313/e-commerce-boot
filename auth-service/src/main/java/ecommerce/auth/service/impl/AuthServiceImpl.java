package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.dto.PhoneLoginDTO;
import ecommerce.auth.dto.SendSmsCodeDTO;
import ecommerce.auth.entity.Admin;
import ecommerce.auth.mapper.AdminMapper;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.SmsCodeVO;
import ecommerce.exception.BusinessException;
import ecommerce.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 验证码缓存 key：auth:sms:code:{phone} */
    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    /** 发送频率限制 key：auth:sms:limit:{phone} */
    private static final String SMS_LIMIT_KEY_PREFIX = "auth:sms:limit:";
    /** 中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** 6 位数字验证码 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    /** 开发模式：不接真实短信通道，验证码只写日志并回传前端；上线前置 false */
    @Value("${sms.dev-mode:true}")
    private boolean smsDevMode;
    /** 验证码有效期（秒） */
    @Value("${sms.code-expire:300}")
    private long smsCodeExpire;
    /** 同一手机号两次发送的最小间隔（秒） */
    @Value("${sms.code-interval:60}")
    private long smsCodeInterval;
    /** 演示手机号：发码接口直接回传验证码且不限频率；留空即关闭 */
    @Value("${sms.test-phone:}")
    private String smsTestPhone;

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
        // 2. 按用户名查管理员；查不到或密码不符统一回「用户名或密码错误」，不暴露账号是否存在
        Admin admin = adminMapper.findByUsername(dto.getUsername().trim());
        // ⚠️ 密码目前明文存储、明文比较（admin 表 password 存明文），仅开发期可用。
        //    上线前必须改成 BCrypt：入库时加密、这里改用 matches 校验，否则等于明文裸奔。
        if (admin == null || !dto.getPassword().equals(admin.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // 3. 账号禁用拦截（status=0）
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        // 4. 签发管理员令牌：claims 带上 username/role/id，前端解 payload 做权限判断
        return buildLoginResult(admin);
    }

    @Override
    public SmsCodeVO sendSmsCode(SendSmsCodeDTO dto) {
        String phone = dto == null || dto.getPhone() == null ? "" : dto.getPhone().trim();
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入正确的手机号");
        }
        boolean testPhone = isTestPhone(phone);
        // 未绑定后台账号的手机号不发码，避免用户拿到验证码后才发现登不进去
        Admin admin = adminMapper.findByPhone(phone);
        // 演示号兜底：生产库没给 admin 绑手机号时也能登录，保证演示入口始终可用
        if (admin == null && testPhone) {
            admin = adminMapper.findByUsername("admin");
        }
        if (admin == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该手机号未绑定后台账号");
        }
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        // 同一手机号 60 秒内只允许发一次，防止接口被刷；演示号不限频率
        if (!testPhone) {
            Boolean allowed = stringRedisTemplate.opsForValue()
                    .setIfAbsent(SMS_LIMIT_KEY_PREFIX + phone, "1", Duration.ofSeconds(smsCodeInterval));
            if (!Boolean.TRUE.equals(allowed)) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
            }
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue()
                .set(SMS_CODE_KEY_PREFIX + phone, code, Duration.ofSeconds(smsCodeExpire));
        // TODO 接入真实短信服务商（阿里云/腾讯云）后，在此处调用 SDK 发送；当前开发模式只写日志
        log.info("[SMS] 手机号 {} 的登录验证码：{}（{} 秒内有效）", phone, code, smsCodeExpire);
        return SmsCodeVO.builder()
                .phone(phone)
                .expireIn(smsCodeExpire)
                // 开发模式或演示手机号直接回传验证码；生产环境其他手机号恒为 null
                .devCode(smsDevMode || testPhone ? code : null)
                .build();
    }

    @Override
    public AdminLoginVO loginByPhone(PhoneLoginDTO dto) {
        String phone = dto == null || dto.getPhone() == null ? "" : dto.getPhone().trim();
        String code = dto == null || dto.getCode() == null ? "" : dto.getCode().trim();
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入正确的手机号");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入 6 位验证码");
        }
        String savedCode = stringRedisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + phone);
        if (savedCode == null || !savedCode.equals(code)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }
        // 验证码一次性使用：校验通过立即删除，防止重复登录
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + phone);

        Admin admin = adminMapper.findByPhone(phone);
        // 演示号兜底：与发码接口保持一致，没绑手机号时落到 admin 账号
        if (admin == null && isTestPhone(phone)) {
            admin = adminMapper.findByUsername("admin");
        }
        if (admin == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "该手机号未绑定后台账号");
        }
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        return buildLoginResult(admin);
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

    /** 是否演示手机号：这类号码发码后直接把验证码返回前端，方便任何人体验 */
    private boolean isTestPhone(String phone) {
        return smsTestPhone != null && !smsTestPhone.isBlank() && smsTestPhone.equals(phone);
    }

    /** 签发令牌并组装登录响应：账号密码登录与手机号登录共用 */
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
