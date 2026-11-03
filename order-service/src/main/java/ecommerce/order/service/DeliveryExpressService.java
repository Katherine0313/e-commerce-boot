package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.DeliveryExpressDTO;
import ecommerce.order.vo.DeliveryExpressVO;

import java.util.List;
import java.util.Map;

/**
 * 快递公司字典 Service（后台快递公司管理 + 下拉数据源）。
 */
public interface DeliveryExpressService {

    /** 后台列表（分页 + 编码/名称模糊 + 状态筛选） */
    PageVO<DeliveryExpressVO> listAll(int page, int pageSize, String keyword, Integer status);

    /** 新建快递公司 */
    DeliveryExpressVO create(DeliveryExpressDTO dto);

    /** 编辑快递公司 */
    DeliveryExpressVO update(Long id, DeliveryExpressDTO dto);

    /** 删除快递公司（软删） */
    void delete(Long id);

    /** 启用中的快递公司（下拉用） */
    List<DeliveryExpressVO> listEnabled();

    /** 快递公司下拉选项（[{value,label}]，与旧的 CourierEnum 形状一致，前端不用改） */
    List<Map<String, String>> courierOptions();
}
