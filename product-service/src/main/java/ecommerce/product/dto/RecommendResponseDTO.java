package ecommerce.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Python 推荐服务的响应体（对应 GET /recommend 返回的 JSON）。
 */
@Data
public class RecommendResponseDTO {
    @JsonProperty("product_ids")
    private List<Long> productIds;

    @JsonProperty("scores")
    private List<Double> scores;
}
