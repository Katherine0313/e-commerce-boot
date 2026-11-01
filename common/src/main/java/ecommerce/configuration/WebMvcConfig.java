package ecommerce.configuration;

import ecommerce.interceptor.JwtInterceptor;
import ecommerce.interceptor.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
            // PC 商城登录/注册（手机号密码、手机号验证码），登录前必然没有 token
            "/api/user/login", "/api/user/register", "/api/user/send-code", "/api/user/login-code",
            // 后台登录图形验证码：登录前必然没有 token
            "/api/auth/captcha",
            // C 端商品浏览（裸路径=列表，/**=详情等子路径），只读接口免登录
            "/api/products", "/api/products/**",
            "/api/categories", "/api/categories/**",
            // 首页聚合（分类 + 猜你喜欢），只读免登录
            "/api/home",
            "/api/tags", "/api/tags/**",
            // 售后状态/原因/类型字典（公共只读，后台与 C 端共用，免登录）
            "/api/after-sales/status-constants",
            // Banner 轮播与文章（PC 商城 / 小程序公共内容，只读 + 浏览量统计，免登录）
            "/api/banners", "/api/banners/**",
            "/api/articles", "/api/articles/**"
    );

    private final JwtInterceptor jwtInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, PermissionInterceptor permissionInterceptor) {

        this.jwtInterceptor = jwtInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦 /api/**：thymeleaf 页面和静态资源不受影响
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 免登录接口见 EXCLUDE_AUTH_PATHS
                .excludePathPatterns(EXCLUDE_AUTH_PATHS.toArray(new String[0]));
        // 权限校验必须排在登录鉴权之后：依赖 JwtInterceptor 写入 UserContext 的 permissions
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_AUTH_PATHS.toArray(new String[0]));
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry){
        // 用户头像等上传文件：/upload/** → 工作目录 upload/
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:upload/");
        // 商品图片：数据库 image 字段存 /image/upload/N.webp，映射到 upload/image/ 下的真实文件。
        // opt/ 是压缩后的图片（分类 200px、商品主图 400px、详情图 1200px），优先命中；找不到再回落原图，
        // 这样不用改数据库里的历史路径，原图也保留可随时回滚。
        registry.addResourceHandler("/image/**")
                .addResourceLocations("file:upload/image/opt/", "file:upload/image/")
                // 图片内容按文件名固定，允许浏览器/小程序长期缓存；更新图片后需清 CDN 缓存
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }


}
