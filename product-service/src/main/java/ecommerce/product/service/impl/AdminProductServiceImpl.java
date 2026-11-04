package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.product.cache.CategoryCacheEvictor;
import ecommerce.common.insurance.InsurancePolicy;
import ecommerce.product.dto.AdminProductDTO;
import ecommerce.product.entity.Product;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.service.AdminProductService;
import ecommerce.product.support.ProductSpecsCodec;
import ecommerce.product.vo.AdminProductVO;
import ecommerce.product.vo.DashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台商品管理 Service 实现。
 */
@Slf4j
@Service
public class AdminProductServiceImpl implements AdminProductService {

    private final ProductMapper productMapper;
    private final ProductSpecsCodec specsCodec;
    private final CategoryCacheEvictor categoryCacheEvictor;

    public AdminProductServiceImpl(ProductMapper productMapper, ProductSpecsCodec specsCodec,
                                   CategoryCacheEvictor categoryCacheEvictor) {
        this.productMapper = productMapper;
        this.specsCodec = specsCodec;
        this.categoryCacheEvictor = categoryCacheEvictor;
    }

    @Override
    public PageVO<AdminProductVO> list(int page, int pageSize, String search, Long categoryId) {
        int offset = (page - 1) * pageSize;
        List<AdminProductVO> list = productMapper
                .listAdmin(search, categoryId, offset, pageSize).stream()
                .map(this::toVO)
                .toList();
        long total = productMapper.countAdmin(search, categoryId);
        return new PageVO<>(list, total, page, pageSize);
    }

    @Override
    public AdminProductVO get(Long id) {
        Product p = productMapper.findByIdAdmin(id);
        if (p == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toVO(p);
    }

    @Override
    public AdminProductVO create(AdminProductDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品名称不能为空");
        }
        Product p = new Product();
        applyDto(p, dto);
        // 后台新增表单没有销售模式字段，且部分 NOT NULL 列插 NULL 会覆盖库默认值，这里统一兜底
        if (p.getSalesMode() == null) p.setSalesMode("spot");
        if (p.getListingStatus() == null) p.setListingStatus(1);
        if (p.getSubTitle() == null) p.setSubTitle("");
        if (p.getBrand() == null) p.setBrand("");
        if (p.getSalesPrice() == null) p.setSalesPrice(BigDecimal.ZERO);
        if (p.getStock() == null) p.setStock(0);
        LocalDateTime now = LocalDateTime.now();
        p.setCreateTime(now);
        p.setUpdateTime(now);
        productMapper.insert(p);
        categoryCacheEvictor.evictAll();
        // 回查一次拿到 DB 默认值（如 tag_id），保证返回体与库一致
        return toVO(productMapper.findByIdAdmin(p.getId()));
    }

    @Override
    public AdminProductVO update(Long id, AdminProductDTO dto) {
        if (productMapper.findByIdAdmin(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        Product p = new Product();
        p.setId(id);
        applyDto(p, dto);
        p.setUpdateTime(LocalDateTime.now());
        productMapper.update(p);
        categoryCacheEvictor.evictAll();
        return toVO(productMapper.findByIdAdmin(id));
    }

    @Override
    public void delete(Long id) {
        int rows = productMapper.delete(id);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        categoryCacheEvictor.evictAll();
    }

    @Override
    public List<String> brands() {
        return productMapper.listBrands();
    }

    @Override
    public DashboardVO dashboard() {
        return productMapper.dashboardStats();
    }

    /** 把 DTO 的非 null 字段写入 Product（局部更新语义） */
    private void applyDto(Product p, AdminProductDTO dto) {
        if (dto.getName() != null) p.setName(dto.getName());
        if (dto.getSubTitle() != null) p.setSubTitle(dto.getSubTitle());
        if (dto.getBrand() != null) p.setBrand(dto.getBrand());
        if (dto.getTitle() != null) p.setTitle(dto.getTitle());
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
        // specs 传 null 不修改；传 [] 清空
        if (dto.getSpecs() != null) p.setSpecs(specsCodec.encode(dto.getSpecs()));
    }

    private AdminProductVO toVO(Product p) {
        return AdminProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .subTitle(p.getSubTitle())
                .brand(p.getBrand())
                .title(p.getTitle())
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
                .specs(specsCodec.decode(p.getSpecs()))
                // 后台商品预览弹窗也展示增值保障，与 C 端同一套规则
                .insuranceOptions(InsurancePolicy.options(p.getCategoryId(), p.getSalesPrice()))
                .createdAt(p.getCreateTime())
                .updatedAt(p.getUpdateTime())
                .build();
    }
}
