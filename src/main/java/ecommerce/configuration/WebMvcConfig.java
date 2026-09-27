package ecommerce.configuration;

import ecommerce.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置：注册登录鉴权拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 免登录的接口清单（登录入口本身）。
     * JwtInterceptor 按它豁免鉴权，OpenApiConfig 补 401/403 文档时引用同一份，避免两处清单各自漂移。
     * 后台管理系统接入后，在这里补上 /api/admin/login。
     */
    public static final List<String> EXCLUDE_AUTH_PATHS = List.of("/api/wxlogin", "/api/login");

    private final JwtInterceptor jwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor) {

        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦 /api/**：thymeleaf 页面和静态资源不受影响
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 免登录接口见 EXCLUDE_AUTH_PATHS
                .excludePathPatterns(EXCLUDE_AUTH_PATHS.toArray(new String[0]));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 头像等上传文件的静态访问：/upload/** 映射到本地 upload/ 目录
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:upload/");
    }
}
