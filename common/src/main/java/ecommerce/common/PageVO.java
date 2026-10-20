package ecommerce.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果，后台管理端列表接口统一返回。
 * <p>
 * 旧 Node 后端列表返回 { data: [...], total } 的扁平结构，Spring 端统一用 Result<PageVO<T>>
 * 承载，前端从 data.list 取当前页、data.total 取总条数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    @Schema(description = "当前页数据列表")
    private List<T> list;
    @Schema(description = "总条数")
    private long total;
    @Schema(description = "当前页（从 1 开始）")
    private long page;
    @Schema(description = "每页条数")
    private long pageSize;
}
