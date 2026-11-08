package ecommerce.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 接口文档（OpenAPI 同步 + 人工补充） */
@Data
public class AdminApiDoc {

    private Long id;
    private String serviceName;
    private String groupName;
    private String apiName;
    private String httpMethod;
    private String path;
    private String summary;
    private String description;
    private String requirePerm;
    private Integer isPublic;
    private String queryParams;
    private String headerParams;
    private String bodyParams;
    private String responseParams;
    private String responseExample;
    private String errorCodes;
    private Integer missing;
    private LocalDateTime syncTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
