package ecommerce.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 接口同步结果 */
@Data
public class ApiSyncResultVO {

    @Schema(description = "新增接口数")
    private Integer added;

    @Schema(description = "更新接口数")
    private Integer updated;

    @Schema(description = "本次未出现、被标记 missing 的接口数")
    private Integer missing;

    @Schema(description = "本次处理的接口总数")
    private Integer total;

    @Schema(description = "成功同步的服务名")
    private java.util.List<String> services;

    @Schema(description = "失败的服务及原因")
    private java.util.List<String> errors;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "同步时间")
    private LocalDateTime syncedAt;
}
