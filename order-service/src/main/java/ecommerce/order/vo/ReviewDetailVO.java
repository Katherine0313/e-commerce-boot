package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端商品评价 VO（/api/reviews/product/{productId} 返回）。
 * <p>
 * 只返回 {@code status=1} 且未删除的评价，所以后台隐藏/删除一条，C 端立刻不显示。
 * <p>
 * 刻意**不返回** orderId / orderNo / userId —— 不泄露订单号，也不暴露用户 id。
 * 虚拟评价的 userName 固定「系统」、userAvatar 为 null，前端据此打「系统评价」标签。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDetailVO {

    @Schema(description = "评价 ID")
    private Long id;

    @Schema(description = "星级：1~5")
    private Integer rating;

    @Schema(description = "旧单值标签，仅用于兼容历史数据")
    private String tag;

    @Schema(description = "评价标签数组，最多 4 个")
    private List<String> tags;

    @Schema(hidden = true)
    @JsonIgnore
    private String tagsJson;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "媒体 JSON（图片/视频）")
    private String images;

    @Schema(description = "评价人昵称（虚拟评价为「系统」）")
    private String userName;

    @Schema(description = "评价人头像（虚拟评价为空）")
    private String userAvatar;

    @Schema(description = "是否虚拟评价：1 虚拟 0 真实（前端据此标「系统评价」）")
    private Integer isVirtual;

    @Schema(description = "商家回复")
    private String reply;

    @Schema(description = "商家回复时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replyTime;

    @Schema(description = "评价时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
