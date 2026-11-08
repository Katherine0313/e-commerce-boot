package ecommerce.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台角色列表/详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRoleVO {
    private Long id;
    private String roleKey;
    private String roleName;
    private Integer status;
    private String remark;
    /** 详情接口返回 */
    private List<Long> menuIds;
    /** 该角色下的账号数（含已禁用） */
    private Long userCount;
    /** 该角色下启用中的账号数，只有它 > 0 时角色才不能删除 */
    private Long enabledUserCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
