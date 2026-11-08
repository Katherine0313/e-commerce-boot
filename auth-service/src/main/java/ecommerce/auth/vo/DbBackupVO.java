package ecommerce.auth.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 备份记录行 */
@Data
public class DbBackupVO {

    @Schema(description = "备份文件全名（含分卷号）")
    private String fileName;

    @Schema(description = "分卷号")
    private Integer part;

    @Schema(description = "压缩后大小")
    private Long sizeBytes;

    @Schema(description = "格式化后的大小")
    private String sizeHuman;

    @Schema(description = "是否 gzip 压缩")
    private Boolean compressed;

    @Schema(description = "本次备份的表数量")
    private Integer tableCount;

    @Schema(description = "状态：SUCCESS / FAILED")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
