package ecommerce.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 批量操作表名入参 */
@Data
public class DbTablesDTO {

    @Schema(description = "表名列表；为空表示全部业务表")
    private List<String> tables;
}
