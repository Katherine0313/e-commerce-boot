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

    /** 模板店（杰米小店）：多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;

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
        return toVOList(cartMapper.listByUser(currentUserId(), TEMPLATE_SHOP_ID));
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
        Product product = productMapper.findById(dto.getProductId(), TEMPLATE_SHOP_ID);
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        Cart existing = cartMapper.findByUserAndProduct(userId, dto.getProductId(), TEMPLATE_SHOP_ID);
        if (existing != null) {
            // 已存在：累加数量并刷新快照（商品可能已改价/改名/保障变化）
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setName(product.getName());
            existing.setImage(product.getImage());
            existing.setPrice(product.getSalesPrice());
            existing.setDescription(product.getDescription());
            existing.setInsuranceDetail(insuranceDetail);
            existing.setInsuranceFee(insuranceFee);
            cartMapper.update(existing);
        } else {
            Cart cart = Cart.builder()
                    .shopId(TEMPLATE_SHOP_ID)
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
        Cart existing = cartMapper.findByUserAndProduct(userId, productId, TEMPLATE_SHOP_ID);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不在购物车中");
        }
        existing.setQuantity(Math.max(1, quantity));
        cartMapper.update(existing);
    }

    @Override
    public void removeItem(Long productId) {
        cartMapper.deleteByProduct(currentUserId(), productId, TEMPLATE_SHOP_ID);
    }

    @Override
    public void clear(List<Long> productIds) {
        Long userId = currentUserId();
        if (productIds != null && !productIds.isEmpty()) {
            cartMapper.deleteByProducts(userId, productIds, TEMPLATE_SHOP_ID);
        } else {
            cartMapper.clear(userId, TEMPLATE_SHOP_ID);
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