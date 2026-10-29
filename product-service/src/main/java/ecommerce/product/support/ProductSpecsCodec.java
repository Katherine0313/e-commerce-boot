package ecommerce.product.support;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.SpecItemDTO;
import ecommerce.product.vo.SpecItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * 商品 specs 的 JSON 编解码。
 * <p>
 * 数据库存 JSON 数组字符串（保留参数顺序），实体用 String，进出接口时转换成有序数组。
 */
@Slf4j
@Component
public class ProductSpecsCodec {

    /** 参数项上限、名称/值长度、序列化总长度（防止脏数据撑爆列表响应） */
    private static final int MAX_ITEMS = 30;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_VALUE_LENGTH = 128;
    private static final int MAX_JSON_LENGTH = 8192;

    private final ObjectMapper objectMapper;

    public ProductSpecsCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 后台入参 -> 数据库 JSON 字符串；null 表示不修改，空列表存 "[]" */
    public String encode(List<SpecItemDTO> items) {
        if (items == null) {
            return null;
        }
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格参数最多 " + MAX_ITEMS + " 项");
        }
        for (SpecItemDTO item : items) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格参数名不能为空");
            }
            if (item.getName().trim().length() > MAX_NAME_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格参数名不能超过 " + MAX_NAME_LENGTH + " 个字");
            }
            if (item.getValue() != null && item.getValue().length() > MAX_VALUE_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格参数值不能超过 " + MAX_VALUE_LENGTH + " 个字");
            }
        }
        try {
            String json = objectMapper.writeValueAsString(items);
            if (json.length() > MAX_JSON_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格参数总长度过长");
            }
            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格参数格式不合法");
        }
    }

    /** 数据库 JSON 字符串 -> 有序数组；脏数据只记日志并返回空列表，避免整页 500 */
    public List<SpecItemVO> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            SpecItemVO[] array = objectMapper.readValue(json, SpecItemVO[].class);
            return array == null ? List.of() : Arrays.asList(array);
        } catch (Exception e) {
            log.warn("商品 specs 反序列化失败，按空参数处理: {}", e.getMessage());
            return List.of();
        }
    }
}
