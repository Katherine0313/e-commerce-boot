package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.AdminCouponDTO;
import ecommerce.order.dto.AdminCouponGrantDTO;
import ecommerce.order.vo.AdminCouponVO;
import ecommerce.order.vo.CouponGrantResultVO;
import ecommerce.order.vo.CouponRecordVO;

/**
 * 后台优惠券管理 Service。
 */
public interface AdminCouponService {

    PageVO<AdminCouponVO> list(int page, int pageSize, String keyword, String status);

    AdminCouponVO get(Long id);

    Long create(AdminCouponDTO dto);

    void update(Long id, AdminCouponDTO dto);

    // 停止发放（未开始或发放中）
    void stop(Long id);

    // 删除（仅未开始且无人领取）
    void delete(Long id);

    // 定向发放 / 全量发放
    CouponGrantResultVO grant(Long id, AdminCouponGrantDTO dto);

    // 领取/使用记录
    PageVO<CouponRecordVO> records(Long id, int page, int pageSize);
}
