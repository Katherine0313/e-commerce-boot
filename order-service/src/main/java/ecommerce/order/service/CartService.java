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

   /** 更新数量（最小 1，商品不在购物车抛 404） */
   void updateQuantity(Long productId, Integer quantity);

   /** 移除某个商品 */
   void removeItem(Long productId);

   /** 清空购物车；productIds 非空则只移除列出的商品 */
   void clear(List<Long> productIds);
}