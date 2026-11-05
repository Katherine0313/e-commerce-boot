package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminMenuDTO;
import ecommerce.auth.entity.AdminMenu;
import ecommerce.auth.mapper.AdminMenuMapper;
import ecommerce.auth.service.AdminMenuService;
import ecommerce.auth.support.AdminTokenInvalidator;
import ecommerce.auth.vo.AdminMenuVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.OperationLog;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 后台菜单管理实现。
 */
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private static final Set<String> MENU_TYPES = Set.of("M", "C", "F");
    /** 父链最大深度，防止脏数据造成死循环 */
    private static final int MAX_PARENT_DEPTH = 64;

    private final AdminMenuMapper adminMenuMapper;
    private final AdminTokenInvalidator adminTokenInvalidator;

    public AdminMenuServiceImpl(AdminMenuMapper adminMenuMapper,
                                AdminTokenInvalidator adminTokenInvalidator) {
        this.adminMenuMapper = adminMenuMapper;
        this.adminTokenInvalidator = adminTokenInvalidator;
    }

    @Override
    public List<AdminMenuVO> manageTree() {
        List<AdminMenu> menus = adminMenuMapper.listAll();
        Map<Long, AdminMenuVO> nodes = new LinkedHashMap<>();
        for (AdminMenu menu : menus) {
            nodes.put(menu.getId(), toVO(menu));
        }
        List<AdminMenuVO> roots = new ArrayList<>();
        for (AdminMenuVO node : nodes.values()) {
            AdminMenuVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        Comparator<AdminMenuVO> bySort = Comparator
                .comparing(AdminMenuVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AdminMenuVO::getId, Comparator.nullsLast(Long::compareTo));
        sortTree(roots, bySort);
        return roots;
    }

    @Override
    @Transactional
    @OperationLog(module = "system", action = "MENU_ADD")
    public Long create(AdminMenuDTO dto) {
        AdminMenu menu = build(dto, null);
        adminMenuMapper.insert(menu);
        LogDetail.add("新增菜单：" + menu.getMenuName());
        return menu.getId();
    }

    @Override
    @Transactional
    @OperationLog(module = "system", action = "MENU_EDIT", bizId = "#id")
    public void update(Long id, AdminMenuDTO dto) {
        AdminMenu existing = requireMenu(id);
        LogDetail.add("修改菜单：" + existing.getMenuName());
        AdminMenu menu = build(dto, id);
        // 有子级时禁止改类型，避免出现按钮下面挂菜单这类非法结构
        if (!Objects.equals(existing.getMenuType(), menu.getMenuType())
                && adminMenuMapper.countChildren(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "存在子菜单，不能修改菜单类型");
        }
        adminMenuMapper.update(menu);
        // 只有权限标识/状态变化才影响鉴权；改名、排序、图标不需要重新登录
        if (!Objects.equals(existing.getPerms(), menu.getPerms())
                || !Objects.equals(existing.getStatus(), menu.getStatus())) {
            adminTokenInvalidator.invalidateAfterCommit(adminMenuMapper.listAdminIdsByMenuId(id));
        }
    }

    @Override
    @Transactional
    @OperationLog(module = "system", action = "MENU_REMOVE", bizId = "#id")
    public void delete(Long id) {
        AdminMenu existing = requireMenu(id);
        LogDetail.add("删除菜单：" + existing.getMenuName());
        if (adminMenuMapper.countChildren(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "存在子菜单，不能删除");
        }
        if (adminMenuMapper.countRoleRefs(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该菜单已分配给角色，不能删除");
        }
        adminMenuMapper.delete(id);
    }

    // ==================== 内部方法 ====================

    private AdminMenu requireMenu(Long id) {
        AdminMenu menu = adminMenuMapper.findById(id);
        if (menu == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    /** 校验并组装实体；id 为 null 表示新增 */
    private AdminMenu build(AdminMenuDTO dto, Long id) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数不能为空");
        }
        String menuType = dto.getMenuType() == null ? "" : dto.getMenuType().trim().toUpperCase(Locale.ROOT);
        if (!MENU_TYPES.contains(menuType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "菜单类型只能是 M/C/F");
        }
        String menuName = trimToNull(dto.getMenuName());
        if (menuName == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "菜单名称不能为空");
        }
        if (menuName.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "菜单名称不能超过 64 个字符");
        }

        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "上级菜单不合法");
        }
        if (parentId == 0 && "F".equals(menuType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "按钮必须挂在菜单下");
        }
        if (parentId > 0) {
            AdminMenu parent = adminMenuMapper.findById(parentId);
            if (parent == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "上级菜单不存在");
            }
            if ("F".equals(parent.getMenuType())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "按钮不能作为上级菜单");
            }
            if ("F".equals(menuType) && !"C".equals(parent.getMenuType())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "按钮必须挂在菜单下");
            }
            if (id != null) {
                assertNoCycle(id, parentId);
            }
        }

        String path = trimToNull(dto.getPath());
        if ("C".equals(menuType)) {
            if (path == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "菜单路由路径不能为空");
            }
            if (!path.startsWith("/")) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "路由路径必须以 / 开头");
            }
        }
        if ("F".equals(menuType)) {
            path = null;
        }
        if (path != null && path.length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "路由路径不能超过 200 个字符");
        }

        String component = "C".equals(menuType) ? trimToNull(dto.getComponent()) : null;
        if (component != null && component.length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "组件路径不能超过 200 个字符");
        }

        String perms = trimToNull(dto.getPerms());
        if (perms != null) {
            if (perms.length() > 100) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "权限标识不能超过 100 个字符");
            }
            if (perms.chars().anyMatch(Character::isWhitespace)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "权限标识不能包含空格");
            }
            if (adminMenuMapper.countPerms(perms, id) > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "权限标识已存在");
            }
        }

        String icon = "F".equals(menuType) ? null : trimToNull(dto.getIcon());
        if (icon != null && icon.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "图标名称不能超过 64 个字符");
        }

        LocalDateTime now = LocalDateTime.now();
        return AdminMenu.builder()
                .id(id)
                .parentId(parentId)
                .menuName(menuName)
                .menuType(menuType)
                .path(path)
                .component(component)
                .perms(perms)
                .icon(icon)
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .visible(validateFlag(dto.getVisible(), 1, "显示状态"))
                .status(validateFlag(dto.getStatus(), 1, "菜单状态"))
                .createTime(id == null ? now : null)
                .updateTime(now)
                .build();
    }

    /** 向上遍历父链，父级不能是自身或自身后代 */
    private void assertNoCycle(Long id, Long parentId) {
        Long cursor = parentId;
        int depth = 0;
        while (cursor != null && cursor > 0 && depth++ < MAX_PARENT_DEPTH) {
            if (Objects.equals(cursor, id)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "不能把菜单移动到自己的子菜单下");
            }
            AdminMenu parent = adminMenuMapper.findById(cursor);
            if (parent == null) {
                return;
            }
            cursor = parent.getParentId();
        }
    }

    private AdminMenuVO toVO(AdminMenu menu) {
        return AdminMenuVO.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .menuName(menu.getMenuName())
                .menuType(menu.getMenuType())
                .path(menu.getPath())
                .component(menu.getComponent())
                .perms(menu.getPerms())
                .icon(menu.getIcon())
                .sortOrder(menu.getSortOrder())
                .visible(menu.getVisible())
                .status(menu.getStatus())
                .createTime(menu.getCreateTime())
                .updateTime(menu.getUpdateTime())
                .children(new ArrayList<>())
                .build();
    }

    private void sortTree(List<AdminMenuVO> nodes, Comparator<AdminMenuVO> comparator) {
        nodes.sort(comparator);
        for (AdminMenuVO node : nodes) {
            sortTree(node.getChildren(), comparator);
        }
    }

    private Integer validateFlag(Integer value, int defaultValue, String label) {
        if (value == null) {
            return defaultValue;
        }
        if (value != 0 && value != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, label + "不合法");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
