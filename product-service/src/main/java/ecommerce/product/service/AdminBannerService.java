package ecommerce.product.service;

import ecommerce.common.PageVO;
import ecommerce.product.dto.AdminBannerDTO;
import ecommerce.product.vo.BannerVO;

/**
 * 后台 Banner 管理服务。
 */
public interface AdminBannerService {

    /** 分页列表（标题 / 位置 / 状态筛选） */
    PageVO<BannerVO> list(int page, int pageSize, String keyword, Integer position, Integer status);

    /** 详情（编辑回填） */
    BannerVO get(Long id);

    /** 新建，返回 Banner ID */
    Long create(AdminBannerDTO dto);

    /** 编辑 */
    void update(Long id, AdminBannerDTO dto);

    /** 启用 / 禁用 */
    void updateStatus(Long id, Integer status);

    /** 逻辑删除 */
    void delete(Long id);
}
