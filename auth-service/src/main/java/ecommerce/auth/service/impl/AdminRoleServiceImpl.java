package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminRoleDTO;
import ecommerce.auth.entity.AdminMenu;
import ecommerce.auth.entity.AdminRole;
import ecommerce.auth.mapper.AdminMenuMapper;
import ecommerce.auth.mapper.AdminRoleMapper;
import ecommerce.auth.mapper.AdminUserRoleMapper;
import ecommerce.auth.service.AdminRoleService;
import ecommerce.auth.vo.AdminRoleVO;
import ecommerce.auth.vo.MenuVO;
import ecommerce.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台角色管理实现。
 */
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminMenuMapper adminMenuMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    public AdminRoleServiceImpl(AdminRoleMapper adminRoleMapper, AdminMenuMapper adminMenuMapper,
                                AdminUserRoleMapper adminUserRoleMapper) {
        this.adminRoleMapper = adminRoleMapper;
        this.adminMenuMapper = adminMenuMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
    }

    @Override
    public List<AdminRoleVO> list() {
        return adminRoleMapper.listRoles().stream().map(this::toVO).toList();
    }

    @Override
    public AdminRoleVO get(Long id) {
        AdminRole role = requireRole(id);
        AdminRoleVO vo = toVO(role);
        vo.setMenuIds(adminRoleMapper.listMenuIds(id));
        return vo;
    }

    @Override
    @Transactional
    public Long create(AdminRoleDTO dto) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色参数不能为空");
        }
        String roleKey = validateRoleKey(dto.getRoleKey());
        if (adminRoleMapper.findByKey(roleKey) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色标识已存在");
        }
        AdminRole role = AdminRole.builder()
                .roleKey(roleKey)
                .roleName(validateRoleName(dto.getRoleName()))
                .status(validateStatus(dto.getStatus()))
                .remark(dto.getRemark())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        adminRoleMapper.insert(role);
        saveMenus(role.getId(), dto.getMenuIds());
        return role.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminRoleDTO dto) {
        AdminRole existing = requireRole(id);
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色参数不能为空");
        }
        Integer status = validateStatus(dto.getStatus());
        // 店铺管理员角色必须保持启用；角色标识创建后不允许修改
        if ("admin".equals(existing.getRoleKey()) && status == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "店铺管理员角色不能停用");
        }
        AdminRole update = AdminRole.builder()
                .id(id)
                .roleKey(existing.getRoleKey())
                .roleName(validateRoleName(dto.getRoleName()))
                .status(status)
                .remark(dto.getRemark())
                .updateTime(LocalDateTime.now())
                .build();
        adminRoleMapper.update(update);
        if (dto.getMenuIds() != null) {
            saveMenus(id, dto.getMenuIds());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminRole role = requireRole(id);
        if ("admin".equals(role.getRoleKey())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "店铺管理员角色不能删除");
        }
        if (adminRoleMapper.countUsers(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该角色下还有账号，不能删除");
        }
        adminRoleMapper.deleteRoleMenus(id);
        adminRoleMapper.delete(id);
    }

    @Override
    @Transactional
    public void assignMenus(Long id, List<Long> menuIds) {
        requireRole(id);
        saveMenus(id, menuIds == null ? List.of() : menuIds);
    }

    @Override
    public List<MenuVO> menuTree() {
        List<AdminMenu> menus = adminMenuMapper.listAllWithButtons();
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

    // ==================== 内部方法 ====================

    private AdminRole requireRole(Long id) {
        AdminRole role = adminRoleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private AdminRoleVO toVO(AdminRole role) {
        return AdminRoleVO.builder()
                .id(role.getId())
                .roleKey(role.getRoleKey())
                .roleName(role.getRoleName())
                .status(role.getStatus())
                .remark(role.getRemark())
                .userCount(adminRoleMapper.countUsers(role.getId()))
                .createTime(role.getCreateTime())
                .build();
    }

    private void saveMenus(Long roleId, List<Long> menuIds) {
        adminRoleMapper.deleteRoleMenus(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        List<Long> distinct = menuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            adminRoleMapper.insertRoleMenus(roleId, distinct);
        }
    }

    private String validateRoleKey(String roleKey) {
        String value = roleKey == null ? "" : roleKey.trim();
        if (!value.matches("^[a-z][a-z0-9_]{1,31}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色标识只能是 2~32 位小写字母、数字或下划线");
        }
        return value;
    }

    private String validateRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色名称不能为空");
        }
        return roleName.trim();
    }

    private Integer validateStatus(Integer status) {
        if (status == null) {
            return 1;
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色状态不合法");
        }
        return status;
    }
}
