package ecommerce.order.service;

import ecommerce.order.dto.AddCartDTO;
import ecommerce.order.vo.CartVO;

import java.util.List;

/**
 * 购物车 Service。
 * <p>
 * 当前登录用户从 UserContext.getUserId() 取（单店模式）。
 */
public interface CartService {

   /** 购物车列表 */
   List<CartVO> getCart();

   /** 购物车商品总件数（首页角标，不查明细） */
   int getCount();

   /** 加入购物车：已存在累加数量，不存在新增 */
   void addItem(AddCartDTO dto);

   /** 更新数量（最小 1，商品不在购物车抛 404）；skuId 为空时按商品匹配旧数据 */
   void updateQuantity(Long productId, Long skuId, Integer quantity);

   /** 移除某个商品；skuId 为空时按商品删除旧数据 */
   void removeItem(Long productId, Long skuId);

   /** 清空购物车；skuIds/productIds 非空则只移除列出的行（skuIds 优先） */
   void clear(List<Long> productIds, List<Long> skuIds);
}