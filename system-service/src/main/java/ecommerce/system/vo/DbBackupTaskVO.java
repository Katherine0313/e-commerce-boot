package ecommerce.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 备份任务进度 */
@Data
public class DbBackupTaskVO {

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "RUNNING / SUCCESS / FAILED")
    private String status;

    @Schema(description = "进度百分比 0~100")
    private Integer percent;

    @Schema(description = "总表数")
    private Integer totalTables;

    @Schema(description = "已完成表数")
    private Integer doneTables;

    @Schema(description = "当前处理表名")
    private String currentTable;

    @Schema(description = "备份文件名基（不含分卷号）")
    private String fileNameBase;

    @Schema(description = "失败原因")
    private String error;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
