package ecommerce.auth.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台账号列表/详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccountVO {
    private Long id;
    private String username;
    private String name;
    private String phone;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleNames;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
