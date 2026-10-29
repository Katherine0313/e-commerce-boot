package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminCategoryDTO;
import ecommerce.product.entity.Category;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.service.AdminCategoryService;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.CategoryVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台分类管理 Service 实现。
 * <p>
 * 分类树/扁平列表直接查库（不走 C 端 Redis 缓存，保证后台实时）。
 */
@Service
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final CategoryMapper categoryMapper;

    public AdminCategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryTreeVO> listTree() {
        return buildTree(categoryMapper.listCategories());
    }

    @Override
    public List<CategoryVO> listFlat() {
        return categoryMapper.listCategories().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryVO create(AdminCategoryDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "分类名称不能为空");
        }
        // 传了父分类则校验其存在（新分类无 id，不会形成循环，只需父分类合法）
        if (dto.getParentId() != null && categoryMapper.findById(dto.getParentId()) == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "父分类不存在");
        }
        Category c = new Category();
        c.setName(dto.getName());
        c.setParentId(dto.getParentId());
        c.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        c.setRecommend(dto.getRecommend() == null ? 0 : dto.getRecommend());
        c.setImage(dto.getImage());
        c.setSortOrder(nextSortOrder(dto.getParentId()));
        categoryMapper.insert(c);
        return toVO(categoryMapper.findById(c.getId()));
    }

    @Override
    public CategoryVO update(Long id, AdminCategoryDTO dto) {
        if (categoryMapper.findById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        // 改 parentId 时防循环：不能是自己或自己的后代
        if (dto.getParentId() != null) {
            checkParentValid(id, dto.getParentId());
        }
        Category c = new Category();
        c.setId(id);
        c.setName(dto.getName());
        c.setParentId(dto.getParentId());
        c.setStatus(dto.getStatus());
        c.setRecommend(dto.getRecommend());
        c.setImage(dto.getImage());
        categoryMapper.update(c);
        return toVO(categoryMapper.findById(id));
    }

    @Override
    public void delete(Long id) {
        int rows = categoryMapper.delete(id);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
    }

    @Override
    public void move(Long id, String direction) {
        if (!"up".equals(direction) && !"down".equals(direction)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "移动方向只能是 up 或 down");
        }
        Category existing = categoryMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        // 同级分类（listCategories 已按 sort_order ASC, id ASC 排序），天然限制一级只和一级、二级只和同父的二级互换
        List<Category> siblings = categoryMapper.listCategories().stream()
                .filter(c -> Objects.equals(c.getParentId(), existing.getParentId()))
                .collect(Collectors.toCollection(ArrayList::new));
        int currentIndex = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(id)) {
                currentIndex = i;
                break;
            }
        }
        int targetIndex = "up".equals(direction) ? currentIndex - 1 : currentIndex + 1;
        if (currentIndex == -1 || targetIndex < 0 || targetIndex >= siblings.size()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "up".equals(direction) ? "已是同级第一项，无法上移" : "已是同级最后一项，无法下移");
        }
        // 交换相邻项后整个同级重编号 1..n，顺便修掉历史跳号
        Category tmp = siblings.get(currentIndex);
        siblings.set(currentIndex, siblings.get(targetIndex));
        siblings.set(targetIndex, tmp);
        for (int i = 0; i < siblings.size(); i++) {
            categoryMapper.updateSortOrder(siblings.get(i).getId(), i + 1);
        }
    }

    /** 同级最大 sort_order + 1，让新分类排到同级末尾 */
    private int nextSortOrder(Long parentId) {
        return categoryMapper.listCategories().stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .mapToInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder())
                .max().orElse(0) + 1;
    }

    /** 沿祖先链向上查，防止把分类挂到自己的后代下形成循环 */
    private void checkParentValid(Long id, Long newParentId) {
        if (newParentId.equals(id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "父分类不能是自身");
        }
        Long cursor = newParentId;
        Set<Long> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(id)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "父分类不能是自己的后代");
            }
            Category parent = categoryMapper.findById(cursor);
            if (parent == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "父分类不存在");
            }
            cursor = parent.getParentId();
        }
    }

    private List<CategoryTreeVO> buildTree(List<Category> categories) {
        Map<Long, List<Category>> childrenMap = categories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<CategoryTreeVO> parents = categories.stream()
                .filter(c -> c.getParentId() == null)
                .map(this::toTreeVO)
                .collect(Collectors.toList());
        for (CategoryTreeVO parent : parents) {
            List<Category> children = childrenMap.get(parent.getId());
            parent.setChildren(children == null ? List.of()
                    : children.stream().map(this::toTreeVO).collect(Collectors.toList()));
        }
        return parents;
    }

    private CategoryTreeVO toTreeVO(Category c) {
        return CategoryTreeVO.builder()
                .id(c.getId())
                .name(c.getName())
                .parentId(c.getParentId())
                .sort(c.getSortOrder())
                .status(c.getStatus())
                .recommend(c.getRecommend())
                .image(c.getImage())
                .productCount(c.getProductCount())
                .build();
    }

    private CategoryVO toVO(Category c) {
        return CategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .parentId(c.getParentId())
                .sort(c.getSortOrder())
                .status(c.getStatus())
                .recommend(c.getRecommend())
                .image(c.getImage())
                .productCount(c.getProductCount())
                .build();
    }
}
