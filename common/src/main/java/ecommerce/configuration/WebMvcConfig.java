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
     * 免登录的接口清单：登录/登出入口 + C 端商品浏览。
     * <p>
     * JwtInterceptor 按它豁免鉴权，OpenApiConfig 补 401/403 文档时引用同一份（同一套 Ant 风格匹配），
     * 避免两处清单各自漂移。
     * <ul>
     *   <li>/api/login 是后台管理系统的登录入口；/api/logout 是无状态登出（前端删本地 token 即可，服务端无需校验）。</li>
     *   <li>商品浏览（列表/详情/分类/标签）免登录：小程序进首页看商品不带 token，加购、下单才需要登录。
     *       后台商品管理走 /api/admin/**，不在此清单内，仍由 JwtInterceptor 按 admin 身份拦截。</li>
     * </ul>
     */
    public static final List<String> EXCLUDE_AUTH_PATHS = List.of(
            "/api/wxlogin", "/api/login", "/api/logout",
            // C 端商品浏览（裸路径=列表，/**=详情等子路径），只读接口免登录
            "/api/products", "/api/products/**",
            "/api/categories", "/api/categories/**",
            "/api/tags", "/api/tags/**",
            // AI 导购助手（对话/推荐免登录；下单阶段三需登录取 userId，届时再单独收紧鉴权）
            "/api/agent", "/api/agent/**"
    );

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
    public void addResourceHandlers(ResourceHandlerRegistry registry){
        // 用户头像等上传文件：/upload/** → 工作目录 upload/
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:upload/");
        // 商品图片：数据库 image 字段存 /image/upload/N.webp，映射到 upload/image/ 下的真实文件
        registry.addResourceHandler("/image/**")
                .addResourceLocations("file:upload/image/");
    }


}
