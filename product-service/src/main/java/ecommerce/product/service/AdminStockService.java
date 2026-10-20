package ecommerce.product.service;

import ecommerce.product.dto.AdminStockDTO;
import ecommerce.product.vo.StockStatusVO;
import ecommerce.product.vo.StockVO;

import java.util.List;

/**
 * 后台库存预警管理 Service（/api/admin/stock）。
 */
public interface AdminStockService {
    // 预警列表（按商品名模糊搜索）
    List<StockVO> list(String search);

    // 库存状态常量（high/normal/low 中文 + 颜色）
    StockStatusVO statusConstants();

    // 创建/更新预警配置（product_id 重复自动转更新）
    void create(AdminStockDTO dto);
}
