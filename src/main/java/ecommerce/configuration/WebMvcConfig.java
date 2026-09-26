package ecommerce.configuration;

import ecommerce.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录鉴权拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor) {

        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦 /api/**：thymeleaf 页面和静态资源不受影响
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 登录接口本身不能要求登录
                        "/api/wxlogin",
                        // PC 端商城登录（账号密码 / 验证码，预留）
                        "/api/login"
                        // 后台管理系统接入后，在这里补上 /api/admin/login
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 头像等上传文件的静态访问：/upload/** 映射到本地 upload/ 目录
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:upload/");
    }
}
