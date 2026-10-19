package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminTagDTO;
import ecommerce.product.entity.Product;
import ecommerce.product.entity.Tag;
import ecommerce.product.mapper.TagMapper;
import ecommerce.product.service.AdminTagService;
import ecommerce.product.vo.TagVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后台标签管理 Service 实现。
 * <p>
 * 标签绑定商品即直接写 products.tag_id（一商品最多一标签），绑定/解绑后重算 tags.product_count。
 */
@Service
public class AdminTagServiceImpl implements AdminTagService {

    private final TagMapper tagMapper;

    public AdminTagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    private Long requireShopId() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问：缺少店铺上下文");
        }
        return shopId;
    }

    @Override
    public List<TagVO> list() {
        return tagMapper.listTags(requireShopId()).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public TagVO create(AdminTagDTO dto) {
        Long shopId = requireShopId();
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "标签名称不能为空");
        }
        checkCodeUnique(dto.getCode(), null, shopId);
        Long operatorId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        Tag t = new Tag();
        t.setShopId(shopId);
        t.setName(dto.getName());
        t.setCode(dto.getCode());
        t.setType(dto.getType() == null ? "custom" : dto.getType());
        t.setColor(dto.getColor() == null ? "#1890ff" : dto.getColor());
        t.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        t.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        t.setCreateTime(now);
        t.setCreatedBy(operatorId);
        t.setUpdateTime(now);
        t.setUpdatedBy(operatorId);
        tagMapper.insert(t);
        return toVO(tagMapper.findById(t.getId(), shopId));
    }

    @Override
    public TagVO update(Long id, AdminTagDTO dto) {
        Long shopId = requireShopId();
        if (tagMapper.findById(id, shopId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "标签不存在");
        }
        checkCodeUnique(dto.getCode(), id, shopId);
        Tag t = new Tag();
        t.setId(id);
        t.setShopId(shopId);
        t.setName(dto.getName());
        t.setCode(dto.getCode());
        t.setType(dto.getType());
        t.setColor(dto.getColor());
        t.setSortOrder(dto.getSortOrder());
        t.setStatus(dto.getStatus());
        t.setUpdateTime(LocalDateTime.now());
        t.setUpdatedBy(UserContext.getUserId());
        tagMapper.update(t);
        return toVO(tagMapper.findById(id, shopId));
    }

    @Override
    public void delete(Long id) {
        int rows = tagMapper.delete(id, requireShopId());
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "标签不存在");
        }
    }

    @Override
    public List<Product> listTagProducts(Long tagId) {
        Long shopId = requireShopId();
        if (tagMapper.findById(tagId, shopId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "标签不存在");
        }
        return tagMapper.listProductsByTag(tagId, shopId);
    }

    @Override
    public void bindProductsToTag(Long tagId, List<Long> productIds) {
        Long shopId = requireShopId();
        if (tagMapper.findById(tagId, shopId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "标签不存在");
        }
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        tagMapper.bindProductsToTag(tagId, productIds, LocalDateTime.now(), shopId);
        tagMapper.refreshProductCounts(shopId);
    }

    @Override
    public void unbindProductTag(Long productId, Long tagId) {
        Long shopId = requireShopId();
        tagMapper.unbindProductTag(productId, tagId, LocalDateTime.now(), shopId);
        tagMapper.refreshProductCounts(shopId);
    }

    @Override
    public void unbindTagProducts(Long tagId, List<Long> productIds) {
        Long shopId = requireShopId();
        if (tagMapper.findById(tagId, shopId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "标签不存在");
        }
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        tagMapper.unbindTagProducts(tagId, productIds, LocalDateTime.now(), shopId);
        tagMapper.refreshProductCounts(shopId);
    }

    /** code 唯一校验：排除当前标签自身（编辑时 code 不变不误报） */
    private void checkCodeUnique(String code, Long excludeId, Long shopId) {
        if (code == null || code.isBlank()) {
            return;
        }
        Tag existing = tagMapper.findByCode(code, shopId);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "标签编码已存在");
        }
    }

    private TagVO toVO(Tag t) {
        return TagVO.builder()
                .id(t.getId())
                .name(t.getName())
                .code(t.getCode())
                .type(t.getType())
                .color(t.getColor())
                .sort(t.getSortOrder())
                .status(t.getStatus())
                .productCount(t.getProductCount())
                .build();
    }
}
