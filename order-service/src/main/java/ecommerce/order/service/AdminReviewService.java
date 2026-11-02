package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.AdminReviewDTO;
import ecommerce.order.dto.AdminReviewQueryDTO;
import ecommerce.order.vo.AdminReviewVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 后台商品评价管理 Service（/api/admin/reviews）。
 * <p>
 * 与买家端 {@link ReviewService} 职责隔离：这里只做后台的查/改/回复/隐藏/删除/导出，
 * **不碰买家提交评价那条链路**（ReviewServiceImpl 一行不动）。
 */
public interface AdminReviewService {

    /** 分页列表（商品名/订单号/星级/是否虚拟/显示状态/创建时间区间筛选） */
    PageVO<AdminReviewVO> list(int page, int pageSize, AdminReviewQueryDTO query);

    /** 详情（含订单号、评价人、虚拟评价创建人） */
    AdminReviewVO detail(Long id);

    /** 新增虚拟评价（order_id/user_id 固定 0，商品名/图从 products 表快照取） */
    AdminReviewVO createVirtual(AdminReviewDTO dto);

    /** 编辑评价（仅虚拟评价可编辑，真实评价直接拒绝） */
    AdminReviewVO update(Long id, AdminReviewDTO dto);

    /** 回复评价（一条评价只保留一条回复，再次回复覆盖） */
    void reply(Long id, String reply);

    /** 切换显示状态：1 显示 0 隐藏 */
    void updateVisible(Long id, Integer visible);

    /** 逻辑删除（is_deleted=1） */
    void delete(Long id);

    /** 导出 Excel（与列表共用同一套筛选条件） */
    void exportExcel(HttpServletResponse response, AdminReviewQueryDTO query);
}
