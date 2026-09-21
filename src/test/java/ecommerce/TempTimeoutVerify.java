package ecommerce;

import ecommerce.utils.HttpClientUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时校验类：验证 RestClient.Builder 注入成功且超时配置真实生效，跑完即删
 */
@SpringBootTest
public class TempTimeoutVerify {

    @Autowired
    private HttpClientUtil httpClientUtil;

    /**
     * 起一个只接受连接、但故意 10 秒不返回任何数据的本地服务。
     * 配了 read-timeout: 5s 的话，应该在 5 秒左右失败，而不是傻等 10 秒。
     */
    @Test
    public void testReadTimeoutTakesEffect() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread fakeServer = new Thread(() -> {
                try (Socket ignored = server.accept()) {
                    Thread.sleep(10_000); // 接受连接后什么都不回
                } catch (Exception ignored) {
                }
            });
            fakeServer.setDaemon(true);
            fakeServer.start();

            long start = System.currentTimeMillis();
            Exception ex = assertThrows(Exception.class,
                    () -> httpClientUtil.get("http://localhost:" + port + "/"));
            long cost = System.currentTimeMillis() - start;

            System.out.println("【读超时】耗时 " + cost + " ms，异常 = "
                    + ex.getClass().getSimpleName() + ": "
                    + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getClass().getSimpleName()));
            assertTrue(cost < 8000, "应该在 5 秒读超时后失败，而不是等到 10 秒，实际耗时 " + cost + " ms");
        }
    }
}
