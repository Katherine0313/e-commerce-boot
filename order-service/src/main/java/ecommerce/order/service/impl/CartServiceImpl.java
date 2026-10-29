package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AddCartDTO;
import ecommerce.order.entity.Cart;
import ecommerce.order.entity.Product;
import ecommerce.order.mapper.CartMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.service.CartService;
import ecommerce.order.vo.CartVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 购物车 Service 实现。
 * <p>
 * 快照字段（name/image/price/description）加购时从 products 表查出，不信任前端传入的价格；
 * 增值保障（insuranceDetail/insuranceFee）由加购弹窗随请求传入，快照到购物车（不选则空数组 / 0）。
 */
@Service
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    public CartServiceImpl(CartMapper cartMapper, ProductMapper productMapper, ObjectMapper objectMapper) {
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CartVO> getCart() {
        return toVOList(cartMapper.listByUser(currentUserId()));
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
        // 增值保障：前端加购弹窗里勾选的保障项随请求带上，不选则空数组 / 0
        BigDecimal insuranceFee = dto.getInsuranceFee() == null ? BigDecimal.ZERO : dto.getInsuranceFee();
        String insuranceDetail = toInsuranceJson(dto.getInsuranceDetail());
        Long userId = currentUserId();

        // 商品必须存在（且属于模板店），否则 404；同时拿快照字段
        Product product = productMapper.findById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        Cart existing = cartMapper.findByUserAndProduct(userId, dto.getProductId());

        // 库存校验：购物车已有数量 + 本次加购不能超过库存
        int stock = product.getStock() == null ? 0 : product.getStock();
        int existingQty = existing == null ? 0 : existing.getQuantity();
        if (existingQty + quantity > stock) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存不足，当前仅剩 " + stock + " 件");
        }

        if (existing != null) {
            // 已存在：累加数量并刷新快照（商品可能已改价/改名/保障变化）
            existing.setQuantity(existingQty + quantity);
            existing.setName(product.getName());
            existing.setImage(product.getImage());
            existing.setPrice(product.getSalesPrice());
            existing.setDescription(product.getDescription());
            existing.setInsuranceDetail(insuranceDetail);
            existing.setInsuranceFee(insuranceFee);
            cartMapper.update(existing);
        } else {
            Cart cart = Cart.builder()
                    .userId(userId)
                    .productId(dto.getProductId())
                    .quantity(quantity)
                    .name(product.getName())
                    .image(product.getImage())
                    .price(product.getSalesPrice())
                    .description(product.getDescription())
                    .insuranceDetail(insuranceDetail)
                    .insuranceFee(insuranceFee)
                    .build();
            cartMapper.insert(cart);
        }
    }

    @Override
    public void updateQuantity(Long productId, Integer quantity) {
        if (quantity == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "quantity 不能为空");
        }
        Long userId = currentUserId();
        Cart existing = cartMapper.findByUserAndProduct(userId, productId);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不在购物车中");
        }

        // 库存校验：目标数量不能超过库存
        Product product = productMapper.findById(productId);
        int stock = product == null ? 0 : (product.getStock() == null ? 0 : product.getStock());
        int target = Math.max(1, quantity);
        if (target > stock) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存不足，当前仅剩 " + stock + " 件");
        }

        existing.setQuantity(target);
        cartMapper.update(existing);
    }

    @Override
    public void removeItem(Long productId) {
        cartMapper.deleteByProduct(currentUserId(), productId);
    }

    @Override
    public void clear(List<Long> productIds) {
        Long userId = currentUserId();
        if (productIds != null && !productIds.isEmpty()) {
            cartMapper.deleteByProducts(userId, productIds);
        } else {
            cartMapper.clear(userId);
        }
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    private List<CartVO> toVOList(List<Cart> carts) {
        return carts.stream().map(this::toVO).toList();
    }

    private CartVO toVO(Cart cart) {
        return CartVO.builder()
                .productId(cart.getProductId())
                .name(cart.getName())
                .image(cart.getImage())
                .price(cart.getPrice())
                .description(cart.getDescription())
                .quantity(cart.getQuantity())
                .insuranceDetail(parseInsurance(cart.getInsuranceDetail()))
                .insuranceFee(cart.getInsuranceFee())
                .stock(cart.getStock())
                .subTotal(calcSubTotal(cart))
                .build();
    }

    /** 单品小计 = (单价 + 增值保障费) × 数量，由快照字段实时计算，不落库 */
    private BigDecimal calcSubTotal(Cart cart) {
        BigDecimal price = cart.getPrice() == null ? BigDecimal.ZERO : cart.getPrice();
        BigDecimal fee = cart.getInsuranceFee() == null ? BigDecimal.ZERO : cart.getInsuranceFee();
        return price.add(fee).multiply(BigDecimal.valueOf(cart.getQuantity()));
    }

    /** 增值保障明细 → JSON 字符串，空或序列化失败返回 "[]" */
    private String toInsuranceJson(List<Map<String, Object>> detail) {
        if (detail == null || detail.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "[]";
        }
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