package ecommerce.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 配置：LocalDateTime 统一输出为 yyyy-MM-dd HH:mm:ss（去掉 ISO 的 T）。
 * <p>
 * 为什么不用 spring.jackson.date-format：那个属性只作用于 java.util.Date，
 * LocalDateTime 由 java.time 序列化器处理，不读该配置，必须显式注册序列化器。
 * <p>
 * 只覆盖序列化，不动反序列化：Jackson 默认仍能接收 ISO（含 T）格式，避免影响请求体解析。
 * 字段上已有 @JsonFormat 的（OrderVO、AfterSaleVO.payAt 等）优先级更高，输出格式相同。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeFormatter() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.addModule(module);
        };
    }
}
