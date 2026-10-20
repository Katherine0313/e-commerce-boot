package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminProductDTO;
import ecommerce.product.entity.Product;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.service.AdminProductService;
import ecommerce.product.vo.AdminProductVO;
import ecommerce.product.vo.DashboardVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台商品管理 Service 实现。
 * <p>
 * 多租户隔离：shopId 取自 UserContext（admin 令牌），不再写死模板店；mapper 查询一律带 shopId 过滤。
 */
@Slf4j
@Service
public class AdminProductServiceImpl implements AdminProductService {

    private final ProductMapper productMapper;

    public AdminProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /** 后台接口必须带 shopId（admin 令牌）；取不到说明是 C 端 user 令牌误入，直接 403 */
    private Long requireShopId() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问：缺少店铺上下文");
        }
        return shopId;
    }

    @Override
    public PageVO<AdminProductVO> list(int page, int pageSize, String search, Long categoryId) {
        Long shopId = requireShopId();
        int offset = (page - 1) * pageSize;
        List<AdminProductVO> list = productMapper
                .listAdmin(search, categoryId, offset, pageSize, shopId).stream()
                .map(this::toVO)
                .toList();
        long total = productMapper.countAdmin(search, categoryId, shopId);
        return new PageVO<>(list, total, page, pageSize);
    }

    @Override
    public AdminProductVO get(Long id) {
        Product p = productMapper.findByIdAdmin(id, requireShopId());
        if (p == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toVO(p);
    }

    @Override
    public AdminProductVO create(AdminProductDTO dto) {
        Long shopId = requireShopId();
        Product p = new Product();
        applyDto(p, dto);
        p.setShopId(shopId);
        LocalDateTime now = LocalDateTime.now();
        p.setCreateTime(now);
        p.setUpdateTime(now);
        productMapper.insert(p);
        // 回查一次拿到 DB 默认值（如 tag_id），保证返回体与库一致
        return toVO(productMapper.findByIdAdmin(p.getId(), shopId));
    }

    @Override
    public AdminProductVO update(Long id, AdminProductDTO dto) {
        Long shopId = requireShopId();
        if (productMapper.findByIdAdmin(id, shopId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        Product p = new Product();
        p.setId(id);
        p.setShopId(shopId);
        applyDto(p, dto);
        p.setUpdateTime(LocalDateTime.now());
        productMapper.update(p);
        return toVO(productMapper.findByIdAdmin(id, shopId));
    }

    @Override
    public void delete(Long id) {
        int rows = productMapper.delete(id, requireShopId());
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
    }

    @Override
    public List<String> brands() {
        return productMapper.listBrands(requireShopId());
    }

    @Override
    public DashboardVO dashboard() {
        return productMapper.dashboardStats(requireShopId());
    }

    /** 把 DTO 的非 null 字段写入 Product（局部更新语义） */
    private void applyDto(Product p, AdminProductDTO dto) {
        if (dto.getName() != null) p.setName(dto.getName());
        if (dto.getSubTitle() != null) p.setSubTitle(dto.getSubTitle());
        if (dto.getBrand() != null) p.setBrand(dto.getBrand());
        if (dto.getDescription() != null) p.setDescription(dto.getDescription());
        if (dto.getImage() != null) p.setImage(dto.getImage());
        if (dto.getBody() != null) p.setBody(dto.getBody());
        if (dto.getCategoryId() != null) p.setCategoryId(dto.getCategoryId());
        if (dto.getSalesMode() != null) p.setSalesMode(dto.getSalesMode());
        if (dto.getListingStatus() != null) p.setListingStatus(dto.getListingStatus());
        if (dto.getSalesPrice() != null) p.setSalesPrice(dto.getSalesPrice());
        if (dto.getMarketPrice() != null) p.setMarketPrice(dto.getMarketPrice());
        if (dto.getCostPrice() != null) p.setCostPrice(dto.getCostPrice());
        if (dto.getProductCode() != null) p.setProductCode(dto.getProductCode());
        if (dto.getStock() != null) p.setStock(dto.getStock());
        if (dto.getWeight() != null) p.setWeight(dto.getWeight());
        if (dto.getVolume() != null) p.setVolume(dto.getVolume());
    }

    private AdminProductVO toVO(Product p) {
        return AdminProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .subTitle(p.getSubTitle())
                .brand(p.getBrand())
                .description(p.getDescription())
                .image(p.getImage())
                .body(p.getBody())
                .categoryId(p.getCategoryId())
                .tagId(p.getTagId())
                .salesMode(p.getSalesMode())
                .listingStatus(p.getListingStatus())
                .salesPrice(p.getSalesPrice())
                .marketPrice(p.getMarketPrice())
                .costPrice(p.getCostPrice())
                .productCode(p.getProductCode())
                .stock(p.getStock())
                .weight(p.getWeight())
                .volume(p.getVolume())
                .createdAt(p.getCreateTime())
                .updatedAt(p.getUpdateTime())
                .build();
    }
}
