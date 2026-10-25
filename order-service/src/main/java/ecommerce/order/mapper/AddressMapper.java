package ecommerce.order.mapper;

import ecommerce.order.entity.Address;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 收货地址 Mapper（order-service 内查 address 表）。
 */
public interface AddressMapper {

   // 某用户地址列表（按 id 倒序，最新在前）
   List<Address> listByUser(@Param("userId") Long userId, @Param("shopId") Long shopId);

   // 查单个地址（按 id + userId + shopId 校验归属，防越权）
   Address findById(@Param("id") Long id, @Param("userId") Long userId, @Param("shopId") Long shopId);

   // 新增，回填自增 id
   int insert(Address address);

   // 更新（按 id + userId + shopId 定位）
   int update(Address address);

   // 删除（按 id + userId + shopId 定位）
   int deleteById(@Param("id") Long id, @Param("userId") Long userId, @Param("shopId") Long shopId);

   // 把某用户所有地址的默认标志清 0（设默认前调用）
   int clearDefault(@Param("userId") Long userId, @Param("shopId") Long shopId);

   // 把某用户最新一条（id 最大）设为默认（删除默认地址后兜底）
   int setDefaultToLatest(@Param("userId") Long userId, @Param("shopId") Long shopId);
}