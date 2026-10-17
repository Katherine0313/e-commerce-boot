package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.entity.Admin;
import ecommerce.auth.mapper.AdminMapper;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.exception.BusinessException;
import ecommerce.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(AdminMapper adminMapper, JwtUtil jwtUtil) {
        this.adminMapper = adminMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public AdminLoginVO login(AdminLoginDTO dto) {
        // 测试日志：打印登录入参（含明文密码，仅测试期用，生产要删或脱敏）
        log.info("管理员登录请求: {}", dto);
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
        // 4. 签发管理员令牌：claims 带上 username/role/shopId/id，前端解 payload 做权限判断
        String token = jwtUtil.generateAdminToken(admin.getId(), admin.getUsername(), admin.getRole(), admin.getShopId());
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
}
