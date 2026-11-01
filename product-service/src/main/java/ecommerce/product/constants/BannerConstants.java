package ecommerce.product.constants;

/**
 * Banner 相关常量。
 * <p>
 * 本项目没有字典表，广告位置、跳转类型这类枚举值统一写成代码常量（后端常量 + 前端下拉选项）。
 */
public final class BannerConstants {

    /** 广告位置：小程序首页轮播 */
    public static final int POSITION_MINI_HOME = 1;
    /** 广告位置：小程序分类页轮播 */
    public static final int POSITION_MINI_CATEGORY = 2;
    /** 广告位置：PC 商城首页轮播 */
    public static final int POSITION_PC_HOME = 11;

    /** 跳转类型：不跳转 */
    public static final int LINK_NONE = 0;
    /** 跳转类型：商品详情 */
    public static final int LINK_PRODUCT = 1;
    /** 跳转类型：文章详情 */
    public static final int LINK_ARTICLE = 2;
    /** 跳转类型：外部链接 */
    public static final int LINK_URL = 3;

    private BannerConstants() {
    }
}
