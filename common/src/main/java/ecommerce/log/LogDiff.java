package ecommerce.log;

import java.util.Objects;
import java.util.function.Function;

/**
 * 变更文案工具：把「改前 → 改后」拼成一句人话，写进 operation_log.detail。
 * <p>
 * 只提供两个方法，不做反射扫描（对比芋道的 @DiffLogField + #_DIFF）：
 * <pre>
 * LogDetail.add(LogDiff.of("销售价", 199, 179));                       // 【销售价】从【199】修改为【179】
 * LogDetail.add(LogDiff.of("售后状态", 1, 2, AfterSaleStatusText::of)); // 状态码翻译成中文
 * </pre>
 * 新旧值相同返回 null（调用方 {@link LogDetail#add(String)} 会自动忽略），
 * 避免日志里出现「从【199】修改为【199】」这种噪音。
 */
public final class LogDiff {

    private LogDiff() {
    }

    /**
     * 生成变更文案。
     *
     * @param label    字段名，如「销售价」
     * @param oldValue 改前的值
     * @param newValue 改后的值
     * @return 变更文案；新旧值相同返回 null
     */
    public static String of(String label, Object oldValue, Object newValue) {
        return of(label, oldValue, newValue, null);
    }

    /**
     * 生成变更文案（带格式化函数）。
     *
     * @param label     字段名
     * @param oldValue  改前的值
     * @param newValue  改后的值
     * @param formatter 值格式化函数（把状态码之类翻译成中文），可为 null
     * @return 变更文案；新旧值相同返回 null
     */
    public static String of(String label, Object oldValue, Object newValue,
                            Function<Object, String> formatter) {
        if (Objects.equals(oldValue, newValue)) {
            return null;
        }
        String oldText = format(oldValue, formatter);
        String newText = format(newValue, formatter);
        return "【" + label + "】从【" + oldText + "】修改为【" + newText + "】";
    }

    /** 值为 null 时显示「空」；否则交给格式化函数处理 */
    private static String format(Object value, Function<Object, String> formatter) {
        if (value == null) {
            return "空";
        }
        if (formatter == null) {
            return String.valueOf(value);
        }
        try {
            String text = formatter.apply(value);
            return text == null ? String.valueOf(value) : text;
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
