package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminAccountDTO;
import ecommerce.auth.entity.Admin;
import ecommerce.auth.entity.AdminRole;
import ecommerce.auth.mapper.AdminMapper;
import ecommerce.auth.mapper.AdminRoleMapper;
import ecommerce.auth.mapper.AdminUserRoleMapper;
import ecommerce.auth.service.AdminAccountService;
import ecommerce.auth.support.PasswordUtil;
import ecommerce.auth.vo.AdminAccountVO;
import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台管理员账号管理实现。
 */
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AdminMapper adminMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    public AdminAccountServiceImpl(AdminMapper adminMapper, AdminRoleMapper adminRoleMapper,
                                   AdminUserRoleMapper adminUserRoleMapper) {
        this.adminMapper = adminMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
    }

    @Override
    public PageVO<AdminAccountVO> list(int page, int pageSize, String keyword, Integer status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        int offset = (safePage - 1) * safePageSize;
        List<AdminAccountVO> list = adminMapper.listAdmins(safeKeyword, status, offset, safePageSize)
                .stream().map(this::toVO).toList();
        long total = adminMapper.countAdmins(safeKeyword, status);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public AdminAccountVO get(Long id) {
        Admin admin = requireAdmin(id);
        return toVO(admin);
    }

    @Override
    @Transactional
    public Long create(AdminAccountDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        String username = dto.getUsername().trim();
        if (!username.matches("^[a-zA-Z0-9_]{3,30}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名只能是 3~30 位字母、数字或下划线");
        }
        if (adminMapper.findByUsername(username) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码至少 6 位");
        }
        List<AdminRole> roles = validateRoles(dto.getRoleIds());
        LocalDateTime now = LocalDateTime.now();
        Admin admin = Admin.builder()
                .username(username)
                .password(PasswordUtil.encode(dto.getPassword()))
                .name(validateName(dto.getName()))
                .phone(validatePhone(dto.getPhone()))
                .role(roles.get(0).getRoleKey())
                .status(validateStatus(dto.getStatus()))
                .createTime(now)
                .updateTime(now)
                .build();
        adminMapper.insert(admin);
        saveRoles(admin.getId(), roles);
        return admin.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminAccountDTO dto) {
        Admin existing = requireAdmin(id);
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数不能为空");
        }
        List<AdminRole> roles = validateRoles(dto.getRoleIds());
        Integer status = validateStatus(dto.getStatus());
        // 超级管理员账号不能禁用、不能丢掉 admin 角色
        if (Objects.equals(id, 1L)) {
            if (status == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "超级管理员账号不能禁用");
            }
            if (roles.stream().noneMatch(r -> "admin".equals(r.getRoleKey()))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "超级管理员必须保留店铺管理员角色");
            }
        }
        if (status == 0 && Objects.equals(UserContext.getUserId(), id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能禁用当前登录账号");
        }
        Admin update = Admin.builder()
                .id(existing.getId())
                .name(validateName(dto.getName()))
                .phone(validatePhone(dto.getPhone()))
                .role(roles.get(0).getRoleKey())
                .status(status)
                .updateTime(LocalDateTime.now())
                .build();
        adminMapper.update(update);
        adminUserRoleMapper.deleteByAdminId(id);
        saveRoles(id, roles);
    }

    @Override
    public void resetPassword(Long id, String password) {
        requireAdmin(id);
        if (password == null || password.length() < 6) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码至少 6 位");
        }
        adminMapper.updatePassword(id, PasswordUtil.encode(password), LocalDateTime.now());
    }

    // ==================== 内部方法 ====================

    private Admin requireAdmin(Long id) {
        Admin admin = adminMapper.findById(id);
        if (admin == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "管理员不存在");
        }
        return admin;
    }

    private AdminAccountVO toVO(Admin admin) {
        List<Long> roleIds = adminUserRoleMapper.listRoleIds(admin.getId());
        List<AdminRole> roles = adminRoleMapper.listRoles().stream()
                .filter(role -> roleIds.contains(role.getId()))
                .toList();
        return AdminAccountVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .name(admin.getName())
                .phone(admin.getPhone())
                .status(admin.getStatus())
                .roleIds(roles.stream().map(AdminRole::getId).toList())
                .roleNames(roles.stream().map(AdminRole::getRoleName).toList())
                .createTime(admin.getCreateTime())
                .build();
    }

    private List<AdminRole> validateRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请至少选择一个角色");
        }
        Map<Long, AdminRole> roleMap = adminRoleMapper.listRoles().stream()
                .collect(Collectors.toMap(AdminRole::getId, Function.identity()));
        List<AdminRole> roles = roleIds.stream().distinct().map(roleMap::get).toList();
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色不存在");
        }
        return roles;
    }

    private void saveRoles(Long adminId, List<AdminRole> roles) {
        for (AdminRole role : roles) {
            adminUserRoleMapper.insert(adminId, role.getId());
        }
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "显示名不能为空");
        }
        return name.trim();
    }

    private String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = phone.trim();
        if (!value.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        return value;
    }

    private Integer validateStatus(Integer status) {
        if (status == null) {
            return 1;
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "账号状态不合法");
        }
        return status;
    }
}
