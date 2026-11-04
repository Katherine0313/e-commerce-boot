package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.OperationLog;
import ecommerce.order.dto.DeliveryCreateDTO;
import ecommerce.order.dto.DeliveryQueryDTO;
import ecommerce.order.entity.DeliveryExpress;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderDelivery;
import ecommerce.order.entity.OrderDeliveryLog;
import ecommerce.order.enums.DeliveryOperateTypeEnum;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.DeliveryExpressMapper;
import ecommerce.order.mapper.OrderDeliveryLogMapper;
import ecommerce.order.mapper.OrderDeliveryMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.service.OrderDeliveryService;
import ecommerce.order.vo.OrderDeliveryLogVO;
import ecommerce.order.vo.OrderDeliveryVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单包裹发货 Service 实现。
 * <p>
 * 三个写入口（ship / resend / update）都有事务：包裹 + 订单状态 + 发货流水同生共死。
 * <p>
 * 并发：订单状态用 {@code updateStatusIfCurrent} 条件更新（WHERE 带 order_status），
 * 双击发货只会有一次成功，另一次拿到 409。
 */
@Service
public class OrderDeliveryServiceImpl implements OrderDeliveryService {

    /** 包裹编号前缀 */
    private static final String PACKAGE_NO_PREFIX = "PKG";
    /** 包裹编号格式：PKG + yyyyMMddHHmmss */
    private static final DateTimeFormatter PACKAGE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderDeliveryMapper orderDeliveryMapper;
    private final OrderDeliveryLogMapper orderDeliveryLogMapper;
    private final DeliveryExpressMapper deliveryExpressMapper;

    public OrderDeliveryServiceImpl(OrderMapper orderMapper, OrderDeliveryMapper orderDeliveryMapper,
                                    OrderDeliveryLogMapper orderDeliveryLogMapper,
                                    DeliveryExpressMapper deliveryExpressMapper) {
        this.orderMapper = orderMapper;
        this.orderDeliveryMapper = orderDeliveryMapper;
        this.orderDeliveryLogMapper = orderDeliveryLogMapper;
        this.deliveryExpressMapper = deliveryExpressMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "delivery", action = "SHIP", bizId = "#orderId")
    public OrderDeliveryVO ship(Long orderId, DeliveryCreateDTO dto) {
        Order order = requireOrder(orderId);
        // 状态机校验：只有「待发货(3)」能到「已发货(4)」；未支付(1)会被拦下
        OrderStatusEnum current = OrderStatusEnum.fromCode(order.getOrderStatus());
        if (current == null || !current.canTransitionTo(OrderStatusEnum.SHIPPED)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "不允许从「" + (current == null ? "未知状态" : current.getAdminDesc()) + "」变更为「"
                            + OrderStatusEnum.SHIPPED.getAdminDesc() + "」");
        }
        DeliveryExpress express = requireEnabledExpress(dto);
        LocalDateTime now = LocalDateTime.now();

        // ① 先条件更新订单状态（WHERE 带 order_status，防并发双击插出两个包裹）
        //    必须排在写包裹之前：order_delivery 有指向 orders 的外键，先插子表会对父行加共享锁，
        //    与并发更新父行（排他锁）的请求互相等待 → InnoDB 死锁（实测报 500）。
        //    先拿父行的排他锁、再插子表，两个并发请求只会有一个 affected=1，另一个 409。
        int affected = orderMapper.updateStatusIfCurrent(order.getId(),
                OrderStatusEnum.PENDING_SHIP.getCode(), OrderStatusEnum.SHIPPED.getCode(),
                express.getCode(), dto.getTrackingNumber(), now);
        if (affected == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单状态已变更，请刷新后重试");
        }
        // 内存里的订单对象同步成发货后的状态（返回给前端的是包裹 + 订单状态）
        order.setOrderStatus(OrderStatusEnum.SHIPPED.getCode());
        order.setShipAt(now);

        // ② 再写包裹
        OrderDelivery delivery = buildDelivery(order, express, dto, DeliveryOperateTypeEnum.CREATE, now);
        orderDeliveryMapper.insert(delivery);

        // ③ 写发货流水（同事务）
        writeLog(delivery, DeliveryOperateTypeEnum.CREATE,
                "发货成功：" + express.getName() + " " + dto.getTrackingNumber(), dto.getRemark(), now);

        return toVO(delivery, order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "delivery", action = "RESEND", bizId = "#orderId")
    public OrderDeliveryVO resend(Long orderId, DeliveryCreateDTO dto) {
        Order order = requireOrder(orderId);
        // 补发前提：订单必须已经是「已发货(4)」（已发货→已发货不在状态机允许集里，直接比对当前状态）
        if (!OrderStatusEnum.SHIPPED.getCode().equals(order.getOrderStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有已发货的订单才能补发");
        }
        DeliveryExpress express = requireEnabledExpress(dto);
        LocalDateTime now = LocalDateTime.now();

        // 补发只追加包裹，不动订单状态（orders.courier/tracking_number 仍指首包裹）
        OrderDelivery delivery = buildDelivery(order, express, dto, DeliveryOperateTypeEnum.RESEND, now);
        orderDeliveryMapper.insert(delivery);

        writeLog(delivery, DeliveryOperateTypeEnum.RESEND,
                "补发包裹：" + express.getName() + " " + dto.getTrackingNumber(), dto.getRemark(), now);

        return toVO(delivery, order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "delivery", action = "UPDATE_DELIVERY", bizId = "#id")
    public OrderDeliveryVO update(Long id, DeliveryCreateDTO dto) {
        OrderDelivery delivery = orderDeliveryMapper.findById(id);
        if (delivery == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "包裹不存在");
        }
        DeliveryExpress express = requireEnabledExpress(dto);
        LocalDateTime now = LocalDateTime.now();

        orderDeliveryMapper.update(id, express.getCode(), express.getName(),
                dto.getTrackingNumber(), dto.getRemark(), now);

        // 首包裹（最早发出的那个）才同步订单上的快递信息：
        // orders.courier/tracking_number 记的是首包裹，补发包裹改了不该把订单指过去
        List<OrderDelivery> packages = orderDeliveryMapper.listByOrderId(delivery.getOrderId());
        boolean isFirst = !packages.isEmpty() && packages.get(0).getId().equals(delivery.getId());
        if (isFirst) {
            orderMapper.updateCourier(delivery.getOrderId(), express.getCode(), dto.getTrackingNumber(), now);
        }

        Order order = orderMapper.findById(delivery.getOrderId());
        writeLog(delivery, DeliveryOperateTypeEnum.UPDATE,
                "修改快递信息：" + express.getName() + " " + dto.getTrackingNumber(), dto.getRemark(), now);

        delivery.setExpressCode(express.getCode());
        delivery.setExpressName(express.getName());
        delivery.setTrackingNumber(dto.getTrackingNumber());
        delivery.setRemark(dto.getRemark());
        return toVO(delivery, order);
    }

    @Override
    public PageVO<OrderDeliveryVO> listAdmin(int page, int pageSize, DeliveryQueryDTO query) {
        DeliveryQueryDTO q = query == null ? new DeliveryQueryDTO() : query;
        int offset = (page - 1) * pageSize;
        List<OrderDeliveryVO> list = orderDeliveryMapper
                .listAdmin(offset, pageSize, q.getOrderNo(), q.getTrackingNumber(), q.getExpressCode(), q.getDeliveryType())
                .stream().map(d -> toVO(d, null)).toList();
        long total = orderDeliveryMapper
                .countAdmin(q.getOrderNo(), q.getTrackingNumber(), q.getExpressCode(), q.getDeliveryType());
        return new PageVO<>(list, total, page, pageSize);
    }

    @Override
    public List<OrderDeliveryLogVO> listLogs(Long orderId) {
        return orderDeliveryLogMapper.listByOrderId(orderId).stream().map(this::toLogVO).toList();
    }

    // ==================== 内部工具 ====================

    /** 订单不存在 → 404 */
    private Order requireOrder(Long orderId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /** 快递公司必须存在且启用；快递公司与单号都不能为空 */
    private DeliveryExpress requireEnabledExpress(DeliveryCreateDTO dto) {
        if (dto == null || dto.getExpressCode() == null || dto.getExpressCode().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择快递公司");
        }
        if (dto.getTrackingNumber() == null || dto.getTrackingNumber().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写快递单号");
        }
        DeliveryExpress express = deliveryExpressMapper.findByCode(dto.getExpressCode().trim());
        if (express == null || !Integer.valueOf(1).equals(express.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "快递公司不存在或未启用");
        }
        return express;
    }

    /** 组装包裹实体（包裹编号 = PKG + 时间戳 + 4 位随机，靠唯一索引兜底撞车） */
    private OrderDelivery buildDelivery(Order order, DeliveryExpress express, DeliveryCreateDTO dto,
                                        DeliveryOperateTypeEnum type, LocalDateTime now) {
        return OrderDelivery.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .packageNo(generatePackageNo(now))
                .expressCode(express.getCode())
                .expressName(express.getName())
                .trackingNumber(dto.getTrackingNumber().trim())
                .deliveryType(type == DeliveryOperateTypeEnum.RESEND ? 2 : 1)
                .deliveryTime(now)
                .remark(dto.getRemark())
                .isDeleted(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 包裹编号：PKG + yyyyMMddHHmmss + 4 位随机数字 */
    private String generatePackageNo(LocalDateTime now) {
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return PACKAGE_NO_PREFIX + now.format(PACKAGE_NO_FORMATTER) + random;
    }

    /** 写发货流水（同步同事务，照抄售后 after_sale_log 的做法） */
    private void writeLog(OrderDelivery delivery, DeliveryOperateTypeEnum type,
                           String content, String remark, LocalDateTime now) {
        Long operatorId = UserContext.getUserId();
        orderDeliveryLogMapper.insert(OrderDeliveryLog.builder()
                .deliveryId(delivery.getId())
                .packageNo(delivery.getPackageNo())
                .orderId(delivery.getOrderId())
                .orderNo(delivery.getOrderNo())
                .operateType(type.getCode())
                .content(content)
                .remark(remark)
                .operatorId(operatorId == null ? 0L : operatorId)
                .operatorType(OrderDeliveryLog.OPERATOR_TYPE_ADMIN)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build());
    }

    /** 实体 → VO（订单快照字段：listAdmin 时由 SQL 带出，其余路径从 order 取） */
    private OrderDeliveryVO toVO(OrderDelivery d, Order order) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(
                d.getOrderStatus() != null ? d.getOrderStatus()
                        : (order == null ? null : order.getOrderStatus()));
        DeliveryOperateTypeEnum type = DeliveryOperateTypeEnum.fromCode(d.getDeliveryType());
        return OrderDeliveryVO.builder()
                .id(d.getId())
                .orderId(d.getOrderId())
                .orderNo(d.getOrderNo())
                .packageNo(d.getPackageNo())
                .expressCode(d.getExpressCode())
                .expressName(d.getExpressName())
                .trackingNumber(d.getTrackingNumber())
                .deliveryType(d.getDeliveryType())
                .deliveryTypeDesc(type == null ? null : type.getDesc())
                .deliveryTime(d.getDeliveryTime())
                .remark(d.getRemark())
                .consignee(d.getConsignee() != null ? d.getConsignee() : (order == null ? null : order.getConsignee()))
                .phone(d.getPhone() != null ? d.getPhone() : (order == null ? null : order.getPhone()))
                .region(d.getRegion() != null ? d.getRegion() : (order == null ? null : order.getRegion()))
                .detailAddress(d.getDetailAddress() != null ? d.getDetailAddress()
                        : (order == null ? null : order.getDetailAddress()))
                .buyerRemark(d.getBuyerRemark() != null ? d.getBuyerRemark()
                        : (order == null ? null : order.getRemark()))
                .orderStatus(status == null ? null : status.getCode())
                .orderStatusDesc(status == null ? null : status.getAdminDesc())
                .signStatus(d.getSignStatus() != null ? d.getSignStatus()
                        : (order == null ? null : order.getSignStatus()))
                .createdAt(d.getCreatedAt())
                .build();
    }

    private OrderDeliveryLogVO toLogVO(OrderDeliveryLog log) {
        DeliveryOperateTypeEnum type = DeliveryOperateTypeEnum.fromCode(log.getOperateType());
        return OrderDeliveryLogVO.builder()
                .id(log.getId())
                .deliveryId(log.getDeliveryId())
                .packageNo(log.getPackageNo())
                .orderId(log.getOrderId())
                .orderNo(log.getOrderNo())
                .operateType(log.getOperateType())
                .operateTypeDesc(type == null ? null : type.getDesc())
                .content(log.getContent())
                .remark(log.getRemark())
                .operatorId(log.getOperatorId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
