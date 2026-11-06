package ecommerce.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规格库候选值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecValueVO {
    private Long id;
    private String value;
}
