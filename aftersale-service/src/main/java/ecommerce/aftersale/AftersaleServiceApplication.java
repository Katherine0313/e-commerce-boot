package ecommerce.aftersale;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 售后服务启动类（仅退款/退货退款/换货）。
 * <p>
 * {@code @EnableTransactionManagement(order = HIGHEST_PRECEDENCE)}：把事务切面放到最外层，
 * 售后变更流水的切面（@Order(LOWEST_PRECEDENCE)）在事务内层 —— 状态变更与流水同事务，
 * 一起提交、一起回滚，不会出现「状态变了但流水没写」。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@MapperScan("ecommerce.aftersale.mapper")
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class AftersaleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AftersaleServiceApplication.class, args);
    }
}
