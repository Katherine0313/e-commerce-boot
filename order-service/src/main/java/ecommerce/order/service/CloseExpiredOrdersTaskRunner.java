package ecommerce.order.service;

import ecommerce.task.MonitoredTaskRunner;
import org.springframework.stereotype.Component;

/**
 * 超时关闭待付款订单：手动执行入口，定时触发由 admin_monitor_task 动态调度。
 */
@Component
public class CloseExpiredOrdersTaskRunner implements MonitoredTaskRunner {

    private final OrderService orderService;

    public CloseExpiredOrdersTaskRunner(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String taskName() {
        return "closeExpiredOrders";
    }

    @Override
    public void run() {
        orderService.sweepExpiredPendingPayOrders();
    }
}
