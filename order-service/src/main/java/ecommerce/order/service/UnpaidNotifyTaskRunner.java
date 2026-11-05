package ecommerce.order.service;

import ecommerce.task.MonitoredTaskRunner;
import org.springframework.stereotype.Component;

/**
 * 待付款订单订阅消息提醒：手动执行入口，定时触发由 admin_monitor_task 动态调度。
 */
@Component
public class UnpaidNotifyTaskRunner implements MonitoredTaskRunner {

    private final OrderService orderService;

    public UnpaidNotifyTaskRunner(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String taskName() {
        return "unpaidNotify";
    }

    @Override
    public void run() {
        orderService.sweepUnpaidNotify();
    }
}
