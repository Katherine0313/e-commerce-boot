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
 * 后台商品评价 VO（/api/admin/reviews 返回）。
 * <p>
 * 驼峰命名（与 OrderVO / AdminPromotionVO 一致；商品模块的 AdminProductVO 是蛇形，那是历史 Node 契约遗留）。
 * images 是媒体 JSON 字符串原样透传，前端自己解析（[{"url":"...","type":"image"}]，type 还有 video）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReviewVO {

    @Schema(description = "评价 ID")
    private Long id;

    @Schema(description = "所属订单 ID（虚拟评价为 0）")
    private Long orderId;

    @Schema(description = "订单号（虚拟评价为空）")
    private String orderNo;

    @Schema(description = "评价人用户 ID（虚拟评价为 0）")
    private Long userId;

    @Schema(description = "评价人昵称（虚拟评价为空）")
    private String userName;

    @Schema(description = "评价人头像（虚拟评价为空）")
    private String userAvatar;

    @Schema(description = "商品 ID")
    private Long productId;

    @Schema(description = "商品名快照")
    private String productName;

    @Schema(description = "商品图快照")
    private String productImage;

    @Schema(description = "星级：1~5")
    private Integer rating;

    @Schema(description = "评价标签数组，最多 4 个")
    private List<String> tags;

    @Schema(hidden = true)
    @JsonIgnore
    private String tagsJson;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "媒体 JSON（图片/视频，数组字符串）")
    private String images;

    @Schema(description = "是否虚拟评价：1 虚拟 0 真实")
    private Integer isVirtual;

    @Schema(description = "虚拟评价创建人（后台管理员 ID）")
    private Long createdBy;

    @Schema(description = "虚拟评价创建人姓名")
    private String createdByName;

    @Schema(description = "显示状态：1 显示 0 隐藏")
    private Integer status;

    @Schema(description = "显示状态文案")
    private String statusDesc;

    @Schema(description = "商家回复")
    private String reply;

    @Schema(description = "商家回复时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replyTime;

    @Schema(description = "评价时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
