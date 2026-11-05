package ecommerce.log;

import java.util.ArrayList;
import java.util.List;

/**
 * 变更内容累加器：把「改前 → 改后」的文案攒起来，写进 operation_log.detail。
 * <p>
 * 为什么不用注解扫描：{@link LogDiff} 只提供两个静态方法，Service 里手动 add 即可，
 * 不引第三方 starter，也不做反射扫描字段。
 * <p>
 * 生命周期：Service 方法内 add → 切面在 finally 里取走并清理。
 * <b>ThreadLocal 不跨线程</b>，异步任务里用不了。
 */
public final class LogDetail {

    /** 本次请求累积的变更文案 */
    private static final ThreadLocal<List<String>> ITEMS = new ThreadLocal<>();

    private LogDetail() {
    }

    /** 追加一条变更文案；空内容直接忽略 */
    public static void add(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> list = ITEMS.get();
        if (list == null) {
            list = new ArrayList<>();
            ITEMS.set(list);
        }
        list.add(text);
    }

    /** 追加多条变更文案 */
    public static void addAll(List<String> texts) {
        if (texts == null) {
            return;
        }
        texts.forEach(LogDetail::add);
    }

    /** 取本次累积的全部文案，用「；」连接；没有则返回 null */
    public static String join() {
        List<String> list = ITEMS.get();
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join("；", list);
    }

    /** 清空，必须由切面在 finally 里调用，避免线程复用串数据 */
    public static void clear() {
        ITEMS.remove();
    }
}
