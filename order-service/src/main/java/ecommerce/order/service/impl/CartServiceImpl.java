package ecommerce.order.service.impl;

import ecommerce.common.insurance.InsurancePolicy;
import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.common.promotion.QuoteItemResult;
import ecommerce.common.promotion.QuoteResult;
import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AddCartDTO;
import ecommerce.order.entity.Cart;
import ecommerce.order.entity.Product;
import ecommerce.order.entity.ProductSku;
import ecommerce.order.mapper.CartMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.mapper.ProductSkuMapper;
import ecommerce.order.service.CartService;
import ecommerce.order.service.PromotionPricingService;
import ecommerce.order.support.InsuranceReviewer;
import ecommerce.order.vo.CartVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车 Service 实现。
 * <p>
 * 快照字段（name/image/price/description）加购时从 products 表查出，不信任前端传入的价格；
 * 增值保障（insuranceDetail/insuranceFee）前端勾选后提交，后端按统一规则复核并重算费用再快照到购物车。
 */
@Slf4j
@Service
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ObjectMapper objectMapper;
    private final PromotionPricingService promotionPricingService;

    public CartServiceImpl(CartMapper cartMapper, ProductMapper productMapper, ProductSkuMapper skuMapper,
                           ObjectMapper objectMapper, PromotionPricingService promotionPricingService) {
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.objectMapper = objectMapper;
        this.promotionPricingService = promotionPricingService;
    }

    @Override
    public List<CartVO> getCart() {
        Long userId = currentUserId();
        List<Cart> carts = cartMapper.listByUser(userId);
        // 购物车只展示直减价；满减要按整单金额算，放到确认页/下单时计算
        Map<Long, QuoteItemResult> priceMap = directPrices(carts, userId);
        return carts.stream().map(c -> toVO(c, priceMap.get(c.getId()))).toList();
    }

    @Override
    public int getCount() {
        return cartMapper.countQuantity(currentUserId());
    }

    @Override
    public void addItem(AddCartDTO dto) {
        if (dto.getProductId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "product_id 不能为空");
        }
        int quantity = dto.getQuantity() == null ? 1 : Math.max(1, dto.getQuantity());
        Long userId = currentUserId();

        // 商品必须存在（且属于模板店），否则 404；同时拿快照字段
        Product product = productMapper.findById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        ProductSku sku = resolveSku(dto.getProductId(), dto.getSkuId());
        BigDecimal skuPrice = sku != null && sku.getPrice() != null ? sku.getPrice() : product.getSalesPrice();

        // 增值保障：不采信前端传来的明细和费用，按后端规则复核
        // （配件类、低价商品本来就不卖保障，前端伪造也会被剔掉）
        BigDecimal activityPrice = activityPrice(product, skuPrice, userId);
        InsurancePolicy.Result reviewed = InsuranceReviewer.review(
                product.getCategoryId(), activityPrice, dto.getInsuranceDetail(), objectMapper);
        BigDecimal insuranceFee = reviewed.fee();
        String insuranceDetail = InsuranceReviewer.toJson(reviewed.options(), objectMapper);

        Cart existing = sku != null
                ? cartMapper.findByUserAndSku(userId, sku.getId())
                : cartMapper.findByUserAndProduct(userId, dto.getProductId());

        // 库存校验：购物车已有数量 + 本次加购不能超过库存（SKU 优先）
        int stock = sku != null && sku.getStock() != null
                ? sku.getStock()
                : (product.getStock() == null ? 0 : product.getStock());
        int existingQty = existing == null ? 0 : existing.getQuantity();
        if (existingQty + quantity > stock) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存不足，当前仅剩 " + stock + " 件");
        }

        if (existing != null) {
            // 已存在：累加数量并刷新快照（商品可能已改价/改名/保障变化）
            existing.setQuantity(existingQty + quantity);
            existing.setSkuId(sku != null ? sku.getId() : existing.getSkuId());
            existing.setSpecJson(sku != null ? sku.getSpecJson() : existing.getSpecJson());
            existing.setSkuCode(sku != null ? sku.getSkuCode() : existing.getSkuCode());
            existing.setName(product.getName());
            existing.setImage(product.getImage());
            existing.setPrice(skuPrice);
            existing.setDescription(product.getDescription());
            existing.setInsuranceDetail(insuranceDetail);
            existing.setInsuranceFee(insuranceFee);
            cartMapper.update(existing);
        } else {
            Cart cart = Cart.builder()
                    .userId(userId)
                    .productId(dto.getProductId())
                    .skuId(sku != null ? sku.getId() : null)
                    .specJson(sku != null ? sku.getSpecJson() : null)
                    .skuCode(sku != null ? sku.getSkuCode() : null)
                    .quantity(quantity)
                    .name(product.getName())
                    .image(product.getImage())
                    .price(skuPrice)
                    .description(product.getDescription())
                    .insuranceDetail(insuranceDetail)
                    .insuranceFee(insuranceFee)
                    .build();
            cartMapper.insert(cart);
        }
    }

    @Override
    public void updateQuantity(Long productId, Long skuId, Integer quantity) {
        if (quantity == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "quantity 不能为空");
        }
        Long userId = currentUserId();
        Cart existing = skuId != null
                ? cartMapper.findByUserAndSku(userId, skuId)
                : cartMapper.findByUserAndProduct(userId, productId);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不在购物车中");
        }

        // 库存校验：目标数量不能超过 SKU 库存，SKU 缺失时回落到商品汇总库存
        ProductSku sku = resolveExistingSku(existing);
        Product product = productMapper.findById(productId);
        int stock = sku != null && sku.getStock() != null
                ? sku.getStock()
                : (product == null || product.getStock() == null ? 0 : product.getStock());
        int target = Math.max(1, quantity);
        if (target > stock) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存不足，当前仅剩 " + stock + " 件");
        }

        existing.setQuantity(target);
        cartMapper.update(existing);
    }

    @Override
    public void removeItem(Long productId, Long skuId) {
        Long userId = currentUserId();
        if (skuId != null) {
            cartMapper.deleteBySku(userId, skuId);
        } else {
            cartMapper.deleteByProduct(userId, productId);
        }
    }

    @Override
    public void clear(List<Long> productIds, List<Long> skuIds) {
        Long userId = currentUserId();
        if (skuIds != null && !skuIds.isEmpty()) {
            cartMapper.deleteBySkus(userId, skuIds);
        } else if (productIds != null && !productIds.isEmpty()) {
            cartMapper.deleteByProducts(userId, productIds);
        } else {
            cartMapper.clear(userId);
        }
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    /**
     * 解析用户选中的 SKU：指定 skuId 时必须属于该商品且已启用；
     * 未指定时回落到商品默认 SKU（单规格/旧客户端），查不到返回 null 走商品级兜底。
     */
    private ProductSku resolveSku(Long productId, Long skuId) {
        ProductSku sku = skuId != null
                ? skuMapper.findById(skuId)
                : skuMapper.findDefaultByProductId(productId);
        if (sku == null) {
            if (skuId != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "所选规格不存在或已下架");
            }
            return null;
        }
        if (!productId.equals(sku.getProductId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "所选规格与商品不匹配");
        }
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "所选规格已下架");
        }
        return sku;
    }

    /** 购物车行对应的 SKU（行上没存时回落到商品默认 SKU） */
    private ProductSku resolveExistingSku(Cart cart) {
        if (cart.getSkuId() != null) {
            ProductSku sku = skuMapper.findById(cart.getSkuId());
            if (sku != null && sku.getStatus() != null && sku.getStatus() == 1) {
                return sku;
            }
        }
        return skuMapper.findDefaultByProductId(cart.getProductId());
    }

    /** 批量算购物车商品的直减价（一次查询活动配置，避免逐条） */
    private Map<Long, QuoteItemResult> directPrices(List<Cart> carts, Long userId) {
        if (carts == null || carts.isEmpty()) {
            return Map.of();
        }
        List<QuoteItemInput> inputs = carts.stream()
                .filter(c -> c.getProductId() != null && c.getPrice() != null)
                .map(c -> QuoteItemInput.builder()
                        .productId(c.getProductId())
                        .quantity(c.getQuantity())
                        .originalPrice(c.getPrice())
                        .build())
                .toList();
        try {
            QuoteResult quote = promotionPricingService.quoteDirect(inputs, userId);
            Map<Long, QuoteItemResult> map = new HashMap<>();
            List<QuoteItemResult> items = quote.getItems();
            // 促销算价按入参顺序逐条返回；同一商品的不同 SKU 也有不同原价，不能按 productId 覆盖
            if (items != null && items.size() == carts.size()) {
                for (int i = 0; i < carts.size(); i++) {
                    map.put(carts.get(i).getId(), items.get(i));
                }
            }
            return map;
        } catch (RuntimeException e) {
            // 促销表未迁移或临时故障时购物车照常展示快照价，不影响加购/结算入口
            log.warn("购物车促销算价失败，退回快照价: {}", e.getMessage());
            return Map.of();
        }
    }

    private CartVO toVO(Cart cart, QuoteItemResult quoted) {
        BigDecimal price = quoted == null ? cart.getPrice() : quoted.getFinalPrice();
        boolean hasPromotion = quoted != null && quoted.getDiscountAmount() != null
                && quoted.getDiscountAmount().signum() > 0;
        return CartVO.builder()
                .productId(cart.getProductId())
                .skuId(cart.getSkuId())
                .specJson(cart.getSpecJson())
                .skuCode(cart.getSkuCode())
                .name(cart.getName())
                .image(cart.getImage())
                .price(price)
                .originalPrice(hasPromotion ? quoted.getOriginalPrice() : null)
                .promotionTag(hasPromotion ? quoted.getPromotionTag() : null)
                .description(cart.getDescription())
                .quantity(cart.getQuantity())
                .insuranceDetail(parseInsurance(cart.getInsuranceDetail()))
                .insuranceFee(cart.getInsuranceFee())
                .stock(cart.getStock())
                .subTotal(calcSubTotal(price, cart.getInsuranceFee(), cart.getQuantity()))
                .build();
    }

    /** 单品小计 = (单价 + 增值保障费) × 数量，由快照字段实时计算，不落库 */
    private BigDecimal calcSubTotal(BigDecimal price, BigDecimal fee, Integer quantity) {
        BigDecimal safePrice = price == null ? BigDecimal.ZERO : price;
        BigDecimal safeFee = fee == null ? BigDecimal.ZERO : fee;
        return safePrice.add(safeFee).multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }

    /**
     * 商品活动价（直减后）：增值保障按活动价计价，和详情页、购物车展示口径一致。
     * 促销算价失败退回售价，不阻断加购。
     */
    private BigDecimal activityPrice(Product product, BigDecimal basePrice, Long userId) {
        BigDecimal price = basePrice == null ? BigDecimal.ZERO : basePrice;
        try {
            QuoteResult quote = promotionPricingService.quoteDirect(
                    List.of(QuoteItemInput.builder()
                            .productId(product.getId())
                            .quantity(1)
                            .originalPrice(price)
                            .build()),
                    userId);
            if (quote != null && !quote.getItems().isEmpty()) {
                BigDecimal finalPrice = quote.getItems().get(0).getFinalPrice();
                if (finalPrice != null) {
                    return finalPrice;
                }
            }
        } catch (RuntimeException e) {
            log.warn("计算商品活动价失败，增值保障按售价计价：productId={}", product.getId(), e);
        }
        return price;
    }

    /** JSON 字符串 → 数组（增值保障明细），解析失败返回空数组 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseInsurance(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return (List<Map<String, Object>>) objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}