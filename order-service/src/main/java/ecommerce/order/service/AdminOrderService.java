package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.AdminOrderDTO;
import ecommerce.order.vo.LogisticsVO;
import ecommerce.order.vo.OrderVO;

import java.util.List;
import java.util.Map;

/**
 * 后台订单管理 Service（/api/admin/orders）。
 * <p>
 * 商家视角：查所有订单（不限用户）、确认收款/发货/完成/取消/删除/批量删除。
 */
public interface AdminOrderService {

    /** 订单列表（分页 + 状态/关键词/时间范围筛选；dateField 可选时间列，默认 created_at） */
    PageVO<OrderVO> listAll(int page, int pageSize, Integer status, String keyword,
                            String startDate, String endDate, String dateField);

    /** 订单详情（含明细） */
    OrderVO getOrder(Long id);

    /** 订单状态常量（map=code→后台文案，color=code→颜色） */
    Map<String, Object> getStatusConstants();

    /** 快递公司列表（[{value,label}]） */
    List<Map<String, String>> getCouriers();

    /** 更新订单信息（收货人/电话/地址/备注） */
    OrderVO updateOrder(Long id, AdminOrderDTO dto);

    /** 确认收款：待确认 → 待发货 */
    OrderVO confirmOrder(Long id);

    /** 发货：待发货 → 已发货 */
    OrderVO shipOrder(Long id, String courier, String trackingNumber);

    /** 取消：恢复库存 + 改取消 */
    OrderVO cancelOrder(Long id);

    /** 删除订单（软删） */
    void deleteOrder(Long id);

    /** 批量删除订单 */
    void batchDelete(List<Long> ids);

    /** 物流轨迹（假数据） */
    LogisticsVO getLogistics(Long id);
}
