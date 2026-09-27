package ecommerce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.net.URI;

/**
 * 只扫描各业务模块下的 mapper 包。
 * 不能写裸 @MapperScan：它默认扫本类所在包（整个 ecommerce），
 * 而 MyBatis 把「任何接口」都当候选（见 ClassPathMapperScanner#isCandidateComponent），
 * 会把 UserService 这类 Service 接口也注册成 Mapper 代理，
 * 结果 WxLoginController 注入到的是代理而不是 UserServiceImpl。
 */
@MapperScan("ecommerce.**.mapper")
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        String url = "http://localhost:8080/";
        System.out.println("首页地址: " + url);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }
}