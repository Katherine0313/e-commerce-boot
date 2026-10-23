package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 提交意见反馈请求体。
 */
@Data
public class CreateFeedbackDTO {
    @Schema(description = "反馈类型：suggestion / product / order / other")
    private String type;
    @Schema(description = "反馈内容，5~1000 字")
    private String content;
    @Schema(description = "截图相对路径数组，最多 6 张（/api/upload?type=feedback 返回）")
    private List<String> images;
    @Schema(description = "联系方式（选填）")
    private String contact;
}
