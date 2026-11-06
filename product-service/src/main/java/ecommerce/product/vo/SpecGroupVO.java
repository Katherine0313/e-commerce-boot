package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规格库条目：一个规格名及其全部候选值。
 */
@Data
public class SpecGroupVO {
    private Long id;
    private Long categoryId;
    private String name;
    private Integer status;
    private List<SpecValueVO> values = new ArrayList<>();
}
