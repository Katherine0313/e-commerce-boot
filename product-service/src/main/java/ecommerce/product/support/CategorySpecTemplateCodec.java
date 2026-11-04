package ecommerce.product.support;

import ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类规格模板的 JSON 编解码。
 * <p>
 * 数据库 categories.spec_template 存 JSON 字符串数组，数组顺序即商品表单中规格参数的展示顺序。
 * 模板项只规定参数名（可带单位，如「重量(kg)」），填写与否由商家决定，所以没有必填概念。
 */
@Slf4j
@Component
public class CategorySpecTemplateCodec {

    /** 单个分类的模板字段上限、字段名长度、序列化总长度 */
    private static final int MAX_ITEMS = 30;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_JSON_LENGTH = 2048;

    private final ObjectMapper objectMapper;

    public CategorySpecTemplateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 后台入参 -> 数据库 JSON 字符串；null 表示不修改，空列表存 "[]" */
    public String encode(List<String> items) {
        if (items == null) {
            return null;
        }
        List<String> normalized = new ArrayList<>(items.size());
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.length() > MAX_NAME_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格模板字段名不能超过 " + MAX_NAME_LENGTH + " 个字");
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
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

    /** 数据库 JSON -> 有序字段名数组；脏数据只记日志并返回空列表 */
    public List<String> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            String[] array = objectMapper.readValue(json, String[].class);
            if (array == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>(array.length);
            for (String item : array) {
                if (item != null && !item.isBlank()) {
                    result.add(item.trim());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("分类 spec_template 反序列化失败，按空模板处理: {}", e.getMessage());
            return List.of();
        }
    }
}
