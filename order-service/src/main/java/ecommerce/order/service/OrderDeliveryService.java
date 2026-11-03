package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.DeliveryCreateDTO;
import ecommerce.order.dto.DeliveryQueryDTO;
import ecommerce.order.vo.OrderDeliveryLogVO;
import ecommerce.order.vo.OrderDeliveryVO;

import java.util.List;

/**
 * 订单包裹发货 Service（后台发货管理）。
 * <p>
 * 发货 = 订单 3→4 + 写包裹 + 写流水；补发 = 只写包裹 + 写流水（不动订单状态）。
 */
public interface OrderDeliveryService {

    /** 发货：待发货(3) → 已发货(4)，写包裹 + 写流水 */
    OrderDeliveryVO ship(Long orderId, DeliveryCreateDTO dto);

    /** 补发：订单保持已发货(4)，追加一个包裹 + 写流水 */
    OrderDeliveryVO resend(Long orderId, DeliveryCreateDTO dto);

    /** 修改包裹的快递信息（改单号/备注），首包裹会同步改订单上的快递信息 */
    OrderDeliveryVO update(Long id, DeliveryCreateDTO dto);

    /** 后台包裹列表（分页 + 订单号/单号/快递公司/发货类型筛选） */
    PageVO<OrderDeliveryVO> listAdmin(int page, int pageSize, DeliveryQueryDTO query);

    /** 某订单的发货流水（按时间正序） */
    List<OrderDeliveryLogVO> listLogs(Long orderId);
}
