package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类规格模板项：参数名 + 控件类型 + 默认值 + 可选值。
 * <p>
 * 新增商品选择分类时，按模板顺序生成规格参数行，值自动填入 defaultValue，商家只需微调或选择。
 * 模板项本身非必填，商家把值清空后该项不会提交。
 */
@Data
public class SpecTemplateItem {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_SELECT = "select";

    @Schema(description = "参数名，可带单位，如「重量(kg)」")
    private String name;
    @Schema(description = "控件类型：text 文本 / number 数字 / select 选项")
    private String type;
    @Schema(description = "新增商品时自动填入的默认值")
    private String defaultValue;
    @Schema(description = "type=select 时的可选值列表")
    private List<String> options = new ArrayList<>();
}
