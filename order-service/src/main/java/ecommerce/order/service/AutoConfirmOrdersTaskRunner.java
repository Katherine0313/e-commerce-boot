package ecommerce.order.service;

import ecommerce.task.MonitoredTaskRunner;
import org.springframework.stereotype.Component;

/**
 * 发货 7 天后自动确认收货：手动执行入口，定时触发由 admin_monitor_task 动态调度。
 */
@Component
public class AutoConfirmOrdersTaskRunner implements MonitoredTaskRunner {

    private final OrderService orderService;

    public AutoConfirmOrdersTaskRunner(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String taskName() {
        return "autoConfirmOrders";
    }

    @Override
    public void run() {
        orderService.sweepAutoConfirmOrders();
    }
}
