package ecommerce.product.support;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.SpecTemplateItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类规格模板的 JSON 编解码。
 * <p>
 * 数据库 categories.spec_template 存 JSON 数组，元素为
 * {name, type, defaultValue, options}，顺序即商品表单中规格参数的展示顺序。
 * 历史数据是纯字符串数组（只有参数名），decode 时按「文本类型 + 空默认值」兼容。
 */
@Slf4j
@Component
public class CategorySpecTemplateCodec {

    /** 单个分类的模板项上限、参数名/默认值长度、选项数、序列化总长度 */
    private static final int MAX_ITEMS = 30;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_VALUE_LENGTH = 128;
    private static final int MAX_OPTIONS = 20;
    private static final int MAX_OPTION_LENGTH = 32;
    private static final int MAX_JSON_LENGTH = 8192;

    private final ObjectMapper objectMapper;

    public CategorySpecTemplateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 后台入参 -> 数据库 JSON 字符串；null 表示不修改，空列表存 "[]" */
    public String encode(List<SpecTemplateItem> items) {
        if (items == null) {
            return null;
        }
        List<SpecTemplateItem> normalized = new ArrayList<>(items.size());
        Set<String> seenNames = new LinkedHashSet<>();
        for (SpecTemplateItem item : items) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                continue;
            }
            String name = item.getName().trim();
            if (name.length() > MAX_NAME_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格模板参数名不能超过 " + MAX_NAME_LENGTH + " 个字");
            }
            if (!seenNames.add(name)) {
                continue;
            }
            String type = normalizeType(item.getType());
            String defaultValue = item.getDefaultValue() == null ? "" : item.getDefaultValue().trim();
            if (defaultValue.length() > MAX_VALUE_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格模板「" + name + "」的默认值不能超过 " + MAX_VALUE_LENGTH + " 个字");
            }
            List<String> options = normalizeOptions(item.getOptions());
            if (SpecTemplateItem.TYPE_SELECT.equals(type)) {
                if (options.isEmpty()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "规格模板「" + name + "」的可选值不能为空");
                }
                if (!defaultValue.isEmpty() && !options.contains(defaultValue)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "规格模板「" + name + "」的默认值必须是可选值之一");
                }
            } else {
                options = List.of();
            }
            SpecTemplateItem normalizedItem = new SpecTemplateItem();
            normalizedItem.setName(name);
            normalizedItem.setType(type);
            normalizedItem.setDefaultValue(defaultValue);
            normalizedItem.setOptions(new ArrayList<>(options));
            normalized.add(normalizedItem);
        }
        if (normalized.size() > MAX_ITEMS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格模板最多 " + MAX_ITEMS + " 项");
        }
        try {
            String json = objectMapper.writeValueAsString(normalized);
            if (json.length() > MAX_JSON_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格模板总长度过长");
            }
            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格模板格式不合法");
        }
    }

    /** 数据库 JSON -> 有序模板项；脏数据只记日志并返回空列表 */
    public List<SpecTemplateItem> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Object[] array = objectMapper.readValue(json, Object[].class);
            if (array == null) {
                return List.of();
            }
            List<SpecTemplateItem> result = new ArrayList<>(array.length);
            for (Object element : array) {
                if (element instanceof String legacyName) {
                    // 历史格式：只有参数名，按文本类型、无默认值处理
                    if (!legacyName.isBlank()) {
                        result.add(buildItem(legacyName.trim(), SpecTemplateItem.TYPE_TEXT, "", List.of()));
                    }
                } else if (element instanceof Map<?, ?> map) {
                    Object nameValue = map.get("name");
                    if (nameValue == null || nameValue.toString().isBlank()) {
                        continue;
                    }
                    Object typeValue = map.get("type");
                    Object defaultValue = map.get("defaultValue");
                    Object optionsValue = map.get("options");
                    List<String> options = List.of();
                    if (optionsValue instanceof List<?> list) {
                        List<String> raw = new ArrayList<>(list.size());
                        for (Object option : list) {
                            if (option != null) {
                                raw.add(option.toString());
                            }
                        }
                        options = normalizeOptions(raw);
                    }
                    result.add(buildItem(nameValue.toString().trim(),
                            normalizeType(typeValue == null ? null : typeValue.toString()),
                            defaultValue == null ? "" : defaultValue.toString().trim(),
                            options));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("分类 spec_template 反序列化失败，按空模板处理: {}", e.getMessage());
            return List.of();
        }
    }

    private SpecTemplateItem buildItem(String name, String type, String defaultValue, List<String> options) {
        SpecTemplateItem item = new SpecTemplateItem();
        item.setName(name);
        item.setType(type);
        item.setDefaultValue(defaultValue);
        item.setOptions(new ArrayList<>(options));
        return item;
    }

    /** 未知类型按文本处理，保证老数据/前端脏值不会让整页 500 */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return SpecTemplateItem.TYPE_TEXT;
        }
        String value = type.trim().toLowerCase();
        if (SpecTemplateItem.TYPE_NUMBER.equals(value) || SpecTemplateItem.TYPE_SELECT.equals(value)) {
            return value;
        }
        return SpecTemplateItem.TYPE_TEXT;
    }

    private List<String> normalizeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(options.size());
        for (String option : options) {
            if (option == null || option.isBlank()) {
                continue;
            }
            String trimmed = option.trim();
            if (trimmed.length() > MAX_OPTION_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格模板可选值不能超过 " + MAX_OPTION_LENGTH + " 个字");
            }
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
            if (result.size() > MAX_OPTIONS) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格模板单个参数最多 " + MAX_OPTIONS + " 个可选值");
            }
        }
        return result;
    }
}
