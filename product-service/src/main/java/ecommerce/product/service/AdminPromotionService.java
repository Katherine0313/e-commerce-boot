package ecommerce.product.service;

import ecommerce.common.PageVO;
import ecommerce.product.dto.AdminPromotionDTO;
import ecommerce.product.vo.AdminPromotionVO;
import ecommerce.product.vo.PromotionStatsVO;

/**
 * 后台促销活动管理 Service。
 */
public interface AdminPromotionService {

    PageVO<AdminPromotionVO> list(int page, int pageSize, String keyword, String status);

    AdminPromotionVO get(Long id);

    Long create(AdminPromotionDTO dto);

    void update(Long id, AdminPromotionDTO dto);

    // 提前结束（仅进行中）
    void end(Long id);

    // 逻辑删除（仅未开始）
    void delete(Long id);

    PromotionStatsVO stats(Long id);
}
