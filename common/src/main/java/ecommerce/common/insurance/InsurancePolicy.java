package ecommerce.common.insurance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 增值保障可选性规则（唯一收口处）。
 * <p>
 * 背景：保障卡片原先写死在前端，所有商品一视同仁 —— 97 元的手机壳也会弹出
 * 「电池无忧 ¥20」「全面保修 ¥0.97」，既不合理也容易被当成套路。规则统一挪到后端，
 * 前端只按接口返回的选项渲染，order-service 加购/下单时也按同一套规则复核。
 * <p>
 * 当前口径（2026-10-31 定）：
 * <ul>
 *   <li>售价低于 {@link #MIN_PRICE} 的商品不出保障：保障费占比过高、基本没有维修价值</li>
 *   <li>数码配件类目不出保障：手机配件 / 电脑配件 / 存储设备 / 充电设备
 *       （壳膜、扩展坞、充电器本身就有品牌质保，再卖保障是重复收费）</li>
 *   <li>「电池无忧」只对有电池的类目开放，排除耳机音箱 / 智能音箱 / 智能照明</li>
 * </ul>
 * 规则要调整就改这里，前后端不用动。
 */
public final class InsurancePolicy {

    /** 售价门槛：低于此价不出保障 */
    public static final BigDecimal MIN_PRICE = new BigDecimal("300");

    /** 不出保障的类目：701 手机配件、702 电脑配件、703 存储设备、704 充电设备 */
    private static final Set<Long> EXCLUDED_CATEGORY_IDS = Set.of(701L, 702L, 703L, 704L);

    /** 不提供「电池无忧」的类目：401/402/403 耳机音箱、801 智能音箱、803 智能照明 */
    private static final Set<Long> NO_BATTERY_CATEGORY_IDS = Set.of(401L, 402L, 403L, 801L, 803L);

    /** 全面保修：商品售价的 1% */
    private static final BigDecimal WARRANTY_RATE = new BigDecimal("0.01");

    /** 一年意外保：商品售价的 2% */
    private static final BigDecimal ACCIDENT_RATE = new BigDecimal("0.02");

    /** 电池无忧：固定 20 元 */
    private static final BigDecimal BATTERY_FEE = new BigDecimal("20");

    private InsurancePolicy() {
    }

    /**
     * 算出该商品可勾选的保障项。
     *
     * @param categoryId 商品类目 id
     * @param price      计价基准（用最终展示价，即活动价，无活动时就是售价）
     * @return 可选保障项，不适用时返回空列表
     */
    public static List<InsuranceOptionVO> options(Long categoryId, BigDecimal price) {
        List<InsuranceOptionVO> options = new ArrayList<>(3);
        if (categoryId == null || price == null) {
            return options;
        }
        // 配件类目和低价商品都不出保障
        if (EXCLUDED_CATEGORY_IDS.contains(categoryId) || price.compareTo(MIN_PRICE) < 0) {
            return options;
        }
        options.add(InsuranceOptionVO.builder()
                .code("warranty")
                .name("全面保修")
                .fee(scale(price.multiply(WARRANTY_RATE)))
                .desc("商品售价 1%")
                .build());
        options.add(InsuranceOptionVO.builder()
                .code("accident")
                .name("一年意外保")
                .fee(scale(price.multiply(ACCIDENT_RATE)))
                .desc("商品售价 2%")
                .build());
        if (!NO_BATTERY_CATEGORY_IDS.contains(categoryId)) {
            options.add(InsuranceOptionVO.builder()
                    .code("battery")
                    .name("电池无忧")
                    .fee(BATTERY_FEE)
                    .desc("固定 20 元")
                    .build());
        }
        return options;
    }

    /**
     * 按规则复核前端传来的保障明细：只保留该商品允许的保障项，费用一律按规则重算。
     *
     * @param categoryId      商品类目 id
     * @param price           计价基准（活动价）
     * @param selectedCodes   前端勾选的保障项编码
     * @return 复核后的明细（code/name/fee）与单件费用合计
     */
    public static Result review(Long categoryId, BigDecimal price, Set<String> selectedCodes) {
        List<InsuranceOptionVO> allowed = options(categoryId, price);
        List<InsuranceOptionVO> kept = new ArrayList<>(allowed.size());
        for (InsuranceOptionVO option : allowed) {
            if (selectedCodes != null && selectedCodes.contains(option.getCode())) {
                kept.add(option);
            }
        }
        BigDecimal fee = BigDecimal.ZERO;
        for (InsuranceOptionVO option : kept) {
            fee = fee.add(option.getFee());
        }
        return new Result(kept, fee);
    }

    /** 金额保留两位小数（四舍五入），与前端展示口径一致 */
    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 复核结果：明细 + 单件费用合计 */
    public record Result(List<InsuranceOptionVO> options, BigDecimal fee) {
    }
}
