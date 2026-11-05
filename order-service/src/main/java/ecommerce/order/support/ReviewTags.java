package ecommerce.order.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ecommerce.exception.BusinessException;
import ecommerce.order.enums.ReviewTagEnum;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 评价标签工具：多选归一化、JSON 读写。
 */
public final class ReviewTags {

    /** 一条评价最多 4 个标签 */
    public static final int MAX = 4;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReviewTags() {
    }

    /**
     * 写入前归一化：去空、去重、校验白名单与数量上限。
     */
    public static List<String> normalize(List<String> tags) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null) {
                    continue;
                }
                String value = tag.trim();
                if (!value.isEmpty()) {
                    set.add(value);
                }
            }
        }
        List<String> result = new ArrayList<>(set);
        if (result.size() > MAX) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最多选择 4 个标签");
        }
        for (String value : result) {
            if (!ReviewTagEnum.contains(value)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的标签：" + value);
            }
        }
        return result;
    }

    /**
     * 解析筛选参数（逗号分隔），只保留合法标签，最多 4 个。
     */
    public static List<String> forQuery(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                set.add(value);
            }
        }
        if (set.size() > MAX) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最多选择 4 个标签");
        }
        List<String> result = new ArrayList<>();
        for (String value : set) {
            if (ReviewTagEnum.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    public static String toJson(List<String> tags) {
        try {
            return MAPPER.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception e) {
            throw new BusinessException("评价标签序列化失败");
        }
    }

    /**
     * 读取 JSON 数组；为空或格式非法时返回空列表。
     */
    public static List<String> parse(String tagsJson) {
        if (tagsJson != null && !tagsJson.isBlank()) {
            try {
                List<String> list = MAPPER.readValue(tagsJson, new TypeReference<List<String>>() {
                });
                if (list != null) {
                    return list;
                }
            } catch (Exception ignored) {
                // 历史脏数据按无标签处理
            }
        }
        return List.of();
    }
}
