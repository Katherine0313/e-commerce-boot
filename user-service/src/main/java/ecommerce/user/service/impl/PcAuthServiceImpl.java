package ecommerce.user.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.user.dto.PcCodeLoginDTO;
import ecommerce.user.dto.PcLoginDTO;
import ecommerce.user.dto.PcRegisterDTO;
import ecommerce.user.dto.PcSendCodeDTO;
import ecommerce.user.entity.User;
import ecommerce.user.mapper.UserMapper;
import ecommerce.user.service.PcAuthService;
import ecommerce.user.sms.SmsSendException;
import ecommerce.user.sms.SmsService;
import ecommerce.user.support.PasswordUtil;
import ecommerce.user.support.PhoneCodeStore;
import ecommerce.user.vo.PcLoginVO;
import ecommerce.user.vo.PcSendCodeVO;
import ecommerce.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PC 商城登录/注册。
 * 验证码 6 位数字存 Redis（5 分钟），通过 SmsService 下发；aliyun.sms.enabled=false 时只打日志不真发。
 */
@Slf4j
@Service
public class PcAuthServiceImpl implements PcAuthService {

    /** 发送限频：pc:auth:code:limit:{phone} */
    private static final String SEND_LIMIT_KEY_PREFIX = "pc:auth:code:limit:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    /** 用户名：3-30 位字母/数字/下划线（与后台 admin 的用户名规则一致） */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    /** 验证码读取（取出即删除）与绑定手机号共用同一份实现 */
    private final PhoneCodeStore phoneCodeStore;
    /** 短信发送：mock 打日志 / 阿里云真实发送，由 aliyun.sms.enabled 切换实现 */
    private final SmsService smsService;

    public PcAuthServiceImpl(UserMapper userMapper, JwtUtil jwtUtil,
                             StringRedisTemplate stringRedisTemplate,
                             PhoneCodeStore phoneCodeStore,
                             SmsService smsService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.phoneCodeStore = phoneCodeStore;
        this.smsService = smsService;
    }

    @Override
    public PcLoginVO loginByPassword(PcLoginDTO dto) {
        String username = requireUsername(dto.getUsername());
        User user = userMapper.findForLoginByUsername(username);
        // 用户名不存在与密码错误统一提示，避免暴露账号是否注册
        if (user == null || user.getPassword() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return buildLoginVO(user);
    }

    @Override
    public PcLoginVO register(PcRegisterDTO dto) {
        String username = requireUsername(dto.getUsername());
        String phone = requirePhone(dto.getPhone());
        String password = requirePassword(dto.getPassword());
        // 用户名唯一（应用层先查，给友好提示而不是等唯一索引报错）
        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "该用户名已被注册，请换一个");
        }
        User exist = userMapper.findForLoginByPhone(phone);
        if (exist != null) {
            // 小程序用户可能只有手机号没有密码，引导走验证码登录
            throw new BusinessException(HttpStatus.CONFLICT,
                    exist.getPassword() == null ? "该手机号已存在，请使用验证码登录" : "该手机号已注册，请直接登录");
        }
        User user = User.builder()
                .openid(newPcOpenid())
                // 昵称没填时用用户名兜底，避免后台会员列表出现空昵称
                .name(username)
                .username(username)
                .phone(phone)
                .password(PasswordUtil.encode(password))
                .build();
        userMapper.insert(user);
        return buildLoginVO(user);
    }

    @Override
    public PcSendCodeVO sendLoginCode(PcSendCodeDTO dto) {
        String phone = requirePhone(dto.getPhone());
        String limitKey = SEND_LIMIT_KEY_PREFIX + phone;
        Boolean allowed;
        try {
            allowed = stringRedisTemplate.opsForValue().setIfAbsent(limitKey, "1", SEND_INTERVAL);
        } catch (RuntimeException e) {
            log.error("PC 登录验证码限频写入 Redis 失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务暂不可用，请稍后重试");
        }
        if (!Boolean.TRUE.equals(allowed)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请 1 分钟后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            stringRedisTemplate.opsForValue().set(PhoneCodeStore.codeKey(phone), code, CODE_TTL);
        } catch (RuntimeException e) {
            // 写入失败时释放限频，允许用户立即重试
            safeDelete(limitKey);
            log.error("PC 登录验证码写入 Redis 失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务暂不可用，请稍后重试");
        }

        // 真实发送（mock 模式只打日志）。失败时释放限频，允许用户立刻重发
        try {
            smsService.sendCode(phone, code);
        } catch (SmsSendException e) {
            safeDelete(limitKey);
            log.error("PC 登录验证码短信发送失败 phone={}", phone, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "短信发送失败，请稍后重试");
        }

        // 不再把验证码返回给前端，本地调试请查看后端日志
        return PcSendCodeVO.builder()
                .success(true)
                .build();
    }

    @Override
    public PcLoginVO loginByCode(PcCodeLoginDTO dto) {
        String phone = requirePhone(dto.getPhone());
        String code = requireCode(dto.getCode());

        String saved;
        try {
            // 取出即删除：验证码一次性使用
            saved = phoneCodeStore.take(phone);
        } catch (RuntimeException e) {
            log.error("PC 登录验证码读取 Redis 失败", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码服务暂不可用，请稍后重试");
        }
        if (saved == null || !saved.equals(code)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }

        User user = userMapper.findForLoginByPhone(phone);
        if (user == null) {
            // 首次使用验证码登录：自动注册
            user = User.builder()
                    .openid(newPcOpenid())
                    .phone(phone)
                    .build();
            userMapper.insert(user);
        }
        return buildLoginVO(user);
    }

    private PcLoginVO buildLoginVO(User user) {
        return PcLoginVO.builder()
                .token(jwtUtil.generateUserToken(user.getId()))
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .build();
    }

    /**
     * 校验用户名：3-30 位字母/数字/下划线，统一转小写存储。
     * 表 collation 是 utf8mb4_general_ci（大小写不敏感），所以用户输入 Tom 也能匹配到 tom。
     */
    private String requireUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名需为 3-30 位字母、数字或下划线");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * PC 账号没有微信 openid，但 user.openid 是 NOT NULL + 唯一键，
     * 这里生成 pc_ 前缀的占位值（3 + 32 = 35 字符，不超过 varchar(45)）。
     */
    private String newPcOpenid() {
        return "pc_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String requirePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入正确的手机号");
        }
        return value;
    }

    private String requirePassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 6 || value.length() > 20) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码长度需为 6-20 位");
        }
        return value;
    }

    private String requireCode(String code) {
        String value = code == null ? "" : code.trim();
        if (!CODE_PATTERN.matcher(value).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入 6 位验证码");
        }
        return value;
    }

    private void safeDelete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("释放验证码限频失败: {}", e.getMessage());
        }
    }
}
