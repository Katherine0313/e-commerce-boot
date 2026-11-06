package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.SpecGroupDTO;
import ecommerce.product.dto.SpecValueDTO;
import ecommerce.product.entity.Category;
import ecommerce.product.entity.SpecGroup;
import ecommerce.product.entity.SpecValue;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.mapper.SpecGroupMapper;
import ecommerce.product.service.SpecCatalogService;
import ecommerce.product.vo.SpecGroupVO;
import ecommerce.product.vo.SpecValueVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规格库管理实现。
 * <p>
 * 字典只提供候选值，商品实际卖哪些值以 products.sku_specs 为准；
 * 因此删除候选值不影响已上架商品，但删除规格名被商品引用时必须拦截。
 */
@Service
public class SpecCatalogServiceImpl implements SpecCatalogService {

    private final SpecGroupMapper specGroupMapper;
    private final CategoryMapper categoryMapper;

    public SpecCatalogServiceImpl(SpecGroupMapper specGroupMapper, CategoryMapper categoryMapper) {
        this.specGroupMapper = specGroupMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<SpecGroupVO> list() {
        return list(null);
    }

    @Override
    public List<SpecGroupVO> list(Long categoryId) {
        List<SpecGroup> groups;
        if (categoryId == null) {
            // 未指定分类，返回全部启用 + 停用（给规格库管理页用）
            groups = specGroupMapper.listGroups();
        } else {
            // 选了分类：自动向上找所有祖先 id，匹配 category_id IS NULL（全局）或 category_id IN (祖先链)
            Set<Long> chain = new LinkedHashSet<>();
            Long cur = categoryId;
            while (cur != null) {
                chain.add(cur);
                Category c = categoryMapper.findById(cur);
                cur = c != null ? c.getParentId() : null;
            }
            groups = specGroupMapper.listGroupsByCategory(new ArrayList<>(chain));
        }
        List<SpecValue> values = specGroupMapper.listValues();
        Map<Long, SpecGroupVO> voMap = new LinkedHashMap<>();
        for (SpecGroup g : groups) {
            SpecGroupVO vo = new SpecGroupVO();
            vo.setId(g.getId());
            vo.setCategoryId(g.getCategoryId());
            vo.setName(g.getName());
            vo.setStatus(g.getStatus());
            voMap.put(g.getId(), vo);
        }
        for (SpecValue v : values) {
            SpecGroupVO group = voMap.get(v.getGroupId());
            if (group != null) {
                group.getValues().add(new SpecValueVO(v.getId(), v.getValue()));
            }
        }
        return new ArrayList<>(voMap.values());
    }

    @Override
    public SpecGroupVO createGroup(SpecGroupDTO dto) {
        String name = requireName(dto);
        if (specGroupMapper.countGroupByName(dto.getCategoryId(), name) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名已存在：" + name);
        }
        LocalDateTime now = LocalDateTime.now();
        SpecGroup group = new SpecGroup();
        group.setCategoryId(dto.getCategoryId());
        group.setName(name);
        group.setSort(0);
        group.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        group.setCreateTime(now);
        group.setUpdateTime(now);
        specGroupMapper.insertGroup(group);
        return toVO(specGroupMapper.findGroupById(group.getId()));
    }

    @Override
    public SpecGroupVO updateGroup(Long id, SpecGroupDTO dto) {
        SpecGroup old = specGroupMapper.findGroupById(id);
        if (old == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "规格名不存在");
        }
        String name = requireName(dto);
        if (!name.equals(old.getName())
                && specGroupMapper.countGroupByName(old.getCategoryId(), name) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名已存在：" + name);
        }
        old.setName(name);
        old.setStatus(dto.getStatus() == null ? old.getStatus() : dto.getStatus());
        old.setUpdateTime(LocalDateTime.now());
        specGroupMapper.updateGroup(old);
        return toVO(specGroupMapper.findGroupById(id));
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        SpecGroup old = specGroupMapper.findGroupById(id);
        if (old == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "规格名不存在");
        }
        if (specGroupMapper.countProductsReferencingGroup(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "规格【" + old.getName() + "】已被商品引用，不能删除，请改为停用");
        }
        specGroupMapper.deleteValuesByGroupId(id);
        specGroupMapper.deleteGroup(id);
    }

    @Override
    public SpecValueVO addValue(SpecValueDTO dto) {
        if (dto.getGroupId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择规格名");
        }
        SpecGroup group = specGroupMapper.findGroupById(dto.getGroupId());
        if (group == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名不存在");
        }
        String value = dto.getValue() == null ? "" : dto.getValue().trim();
        if (value.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格值不能为空");
        }
        if (value.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格值不能超过 64 个字");
        }
        if (specGroupMapper.countValue(dto.getGroupId(), value) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格值已存在：" + value);
        }
        SpecValue entity = new SpecValue();
        entity.setGroupId(dto.getGroupId());
        entity.setValue(value);
        entity.setSort(0);
        entity.setCreateTime(LocalDateTime.now());
        specGroupMapper.insertValue(entity);
        return new SpecValueVO(entity.getId(), entity.getValue());
    }

    @Override
    public void deleteValue(Long id) {
        specGroupMapper.deleteValue(id);
    }

    private String requireName(SpecGroupDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名不能为空");
        }
        if (name.length() > 32) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名不能超过 32 个字");
        }
        return name;
    }

    private SpecGroupVO toVO(SpecGroup group) {
        SpecGroupVO vo = new SpecGroupVO();
        vo.setId(group.getId());
        vo.setCategoryId(group.getCategoryId());
        vo.setName(group.getName());
        vo.setStatus(group.getStatus());
        return vo;
    }
}
