package ecommerce.order.mapper;

import ecommerce.order.entity.DeliveryExpress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 快递公司字典 Mapper（delivery_express 表）。
 */
@Mapper
public interface DeliveryExpressMapper {

    /** 新增快递公司（回填自增 id） */
    int insert(DeliveryExpress express);

    /** 按 ID 查（不含软删除） */
    DeliveryExpress findById(@Param("id") Long id);

    /** 按编码查（不含软删除） */
    DeliveryExpress findByCode(@Param("code") String code);

    /** 编辑快递公司 */
    int update(DeliveryExpress express);

    /** 软删除（is_deleted=1） */
    int delete(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 后台列表（分页 + 编码/名称模糊 + 状态筛选） */
    List<DeliveryExpress> listAdmin(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                    @Param("keyword") String keyword, @Param("status") Integer status);

    /** 后台总数（与 listAdmin 同筛选条件） */
    long countAdmin(@Param("keyword") String keyword, @Param("status") Integer status);

    /** 启用中的快递公司（下拉用，按排序取） */
    List<DeliveryExpress> listEnabled();

    /** 全部快递公司（不含软删除，含停用），供列表分页一次性加载字典 */
    List<DeliveryExpress> listAll();
}
