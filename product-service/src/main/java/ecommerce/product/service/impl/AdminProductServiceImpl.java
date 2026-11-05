package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.LogDiff;
import ecommerce.log.OperationLog;
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
import java.util.Objects;

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
    @OperationLog(module = "product", action = "PRODUCT_ADD")
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
        AdminProductVO vo = toVO(productMapper.findByIdAdmin(p.getId()));
        // 新增没有「改前值」，只记关键识别信息，方便在日志里定位是哪件商品
        LogDetail.add("新增商品：名称【" + vo.getName() + "】、销售价【" + vo.getSalesPrice()
                + "】、初始库存【" + vo.getStock() + "】");
        return vo;
    }

    @Override
    @OperationLog(module = "product", action = "PRODUCT_EDIT", bizId = "#id")
    public AdminProductVO update(Long id, AdminProductDTO dto) {
        // 复用这次查询的结果：既判断存在性，又当「改前值」算变更，不重复查库
        Product old = productMapper.findByIdAdmin(id);
        if (old == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        Product p = new Product();
        p.setId(id);
        applyDto(p, dto);
        // 上下架、改价、改库存都走这一个接口，一次提交只打一条日志，变更字段在这里逐项列出
        recordDiff(old, dto);
        p.setUpdateTime(LocalDateTime.now());
        productMapper.update(p);
        categoryCacheEvictor.evictAll();
        return toVO(productMapper.findByIdAdmin(id));
    }

    @Override
    @OperationLog(module = "product", action = "PRODUCT_REMOVE", bizId = "#id")
    public void delete(Long id) {
        // 先查一次拿商品名称，日志里要能看出删的是哪件商品
        Product old = productMapper.findByIdAdmin(id);
        int rows = productMapper.delete(id);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        if (old != null) {
            LogDetail.add("删除商品：名称【" + old.getName() + "】");
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

    /**
     * 记录商品变更内容（写进 operation_log.detail）。
     * <p>
     * 上下架（listingStatus）、改价（salesPrice）、改库存（stock）都在 update 一个接口里，
     * 一次提交只打一条日志，这里按「实际变了哪些字段」逐项列出。
     * <p>
     * DTO 里为 null 的字段按 applyDto 的语义是「不修改」，所以不参与比较，
     * 避免出现「从【xxx】修改为【空】」这种没提交过的假变更。
     * <p>
     * LogDiff.of 在新旧值相同时返回 null，LogDetail.add 会自动忽略，值没变就不记。
     */
    private void recordDiff(Product old, AdminProductDTO dto) {
        if (dto.getName() != null) {
            LogDetail.add(LogDiff.of("商品名称", old.getName(), dto.getName()));
        }
        if (dto.getSubTitle() != null) {
            LogDetail.add(LogDiff.of("副标题", old.getSubTitle(), dto.getSubTitle()));
        }
        if (dto.getBrand() != null) {
            LogDetail.add(LogDiff.of("品牌", old.getBrand(), dto.getBrand()));
        }
        if (dto.getTitle() != null) {
            LogDetail.add(LogDiff.of("主标题", old.getTitle(), dto.getTitle()));
        }
        if (dto.getImage() != null) {
            LogDetail.add(LogDiff.of("主图", old.getImage(), dto.getImage()));
        }
        if (dto.getBody() != null && !Objects.equals(old.getBody(), dto.getBody())) {
            // 图文详情是富文本，整段塞进日志会超长（detail 只有 1024），只记「改过」
            LogDetail.add("图文详情已修改");
        }
        if (dto.getCategoryId() != null) {
            LogDetail.add(LogDiff.of("所属分类", old.getCategoryId(), dto.getCategoryId()));
        }
        if (dto.getSalesMode() != null) {
            LogDetail.add(LogDiff.of("销售模式", old.getSalesMode(), dto.getSalesMode()));
        }
        if (dto.getListingStatus() != null) {
            LogDetail.add(LogDiff.of("上架状态", old.getListingStatus(), dto.getListingStatus(),
                    AdminProductServiceImpl::listingStatusText));
        }
        // 价格是 BigDecimal，先按数值比较，199 与 199.00 是同一个价，不该记成变更
        if (dto.getSalesPrice() != null && !sameAmount(old.getSalesPrice(), dto.getSalesPrice())) {
            LogDetail.add(LogDiff.of("销售价", old.getSalesPrice(), dto.getSalesPrice()));
        }
        if (dto.getMarketPrice() != null && !sameAmount(old.getMarketPrice(), dto.getMarketPrice())) {
            LogDetail.add(LogDiff.of("市场价", old.getMarketPrice(), dto.getMarketPrice()));
        }
        if (dto.getCostPrice() != null && !sameAmount(old.getCostPrice(), dto.getCostPrice())) {
            LogDetail.add(LogDiff.of("成本价", old.getCostPrice(), dto.getCostPrice()));
        }
        if (dto.getProductCode() != null) {
            LogDetail.add(LogDiff.of("商品编码", old.getProductCode(), dto.getProductCode()));
        }
        if (dto.getStock() != null) {
            LogDetail.add(LogDiff.of("库存", old.getStock(), dto.getStock()));
        }
        if (dto.getSpecs() != null) {
            // 规格是 JSON 数组，不整段进日志，只记规格项数量
            LogDetail.add(LogDiff.of("规格项数量",
                    specsCodec.decode(old.getSpecs()).size(), dto.getSpecs().size()));
        }
    }

    /**
     * 金额比较：BigDecimal 用数值比较而不是 Objects.equals，
     * 否则 199 与 199.00 会被误判成「改过价」。
     */
    private static boolean sameAmount(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null) {
            return Objects.equals(oldValue, newValue);
        }
        return oldValue.compareTo(newValue) == 0;
    }

    /** 上架状态翻译：1 已上架 / 0 已下架 */
    private static String listingStatusText(Object value) {
        if (value instanceof Integer status) {
            return status == 1 ? "已上架" : "已下架";
        }
        return String.valueOf(value);
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
