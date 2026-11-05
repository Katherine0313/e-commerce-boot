package ecommerce.order.enums;

import java.util.ArrayList;
import java.util.List;

/**
 * 评价标签固定 8 个（4 正向 + 4 负向）。
 * <p>
 * 标签值直接存中文，不做 code 映射；一条评价最多选 4 个。
 */
public enum ReviewTagEnum {

    QUALITY_GOOD("质量很好"),
    VALUE_GOOD("性价比高"),
    LOGISTICS_FAST("物流很快"),
    SERVICE_GOOD("服务周到"),
    NEED_IMPROVE("有待改进"),
    LOGISTICS_SLOW("物流慢"),
    PACKAGE_DAMAGED("包装破损"),
    NOT_AS_DESCRIBED("与描述不符");

    private final String label;

    ReviewTagEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean contains(String value) {
        for (ReviewTagEnum tag : values()) {
            if (tag.label.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> labels() {
        List<String> labels = new ArrayList<>(values().length);
        for (ReviewTagEnum tag : values()) {
            labels.add(tag.label);
        }
        return labels;
    }
}
