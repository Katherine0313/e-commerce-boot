package ecommerce.auth.service;

import ecommerce.auth.dto.ApiDocUpdateDTO;
import ecommerce.auth.entity.AdminApiDoc;
import ecommerce.auth.mapper.AdminApiDocMapper;
import ecommerce.auth.vo.ApiGroupVO;
import ecommerce.auth.vo.ApiItemVO;
import ecommerce.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 接口文档查询、树组装、人工字段编辑 */
@Service
public class ApiDocService {

    private final AdminApiDocMapper adminApiDocMapper;
    private final ObjectMapper objectMapper;

    public ApiDocService(AdminApiDocMapper adminApiDocMapper, ObjectMapper objectMapper) {
        this.adminApiDocMapper = adminApiDocMapper;
        this.objectMapper = objectMapper;
    }

    public List<String> listServices() {
        return adminApiDocMapper.listServices();
    }

    /** 按服务/关键字取接口，组装成"分组 -> 接口"的树 */
    public List<ApiGroupVO> tree(String service, String keyword) {
        List<AdminApiDoc> docs = adminApiDocMapper.listDocs(service, keyword);
        Map<String, List<AdminApiDoc>> grouped = new LinkedHashMap<>();
        for (AdminApiDoc doc : docs) {
            grouped.computeIfAbsent(doc.getGroupName(), key -> new ArrayList<>()).add(doc);
        }

        List<ApiGroupVO> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<AdminApiDoc>> entry : grouped.entrySet()) {
            ApiGroupVO group = new ApiGroupVO();
            group.setKey("g:" + entry.getKey());
            group.setTitle(entry.getKey());
            group.setServiceName(entry.getValue().isEmpty() ? service : entry.getValue().get(0).getServiceName());
            group.setCount(entry.getValue().size());
            List<ApiItemVO> children = new ArrayList<>(entry.getValue().size());
            for (AdminApiDoc doc : entry.getValue()) {
                children.add(toItem(doc));
            }
            group.setChildren(children);
            result.add(group);
        }
        return result;
    }

    public AdminApiDoc detail(Long id) {
        AdminApiDoc doc = adminApiDocMapper.findById(id);
        if (doc == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "接口不存在");
        }
        return doc;
    }

    /** 只更新人工字段；method/path/参数由同步覆盖 */
    public AdminApiDoc update(Long id, ApiDocUpdateDTO dto) {
        AdminApiDoc doc = detail(id);
        String apiName = dto.getApiName() == null ? null : dto.getApiName().trim();
        if (apiName != null && apiName.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "接口名不能超过 128 个字符");
        }
        Integer isPublic = dto.getIsPublic() == null ? 0 : (dto.getIsPublic() == 1 ? 1 : 0);
        String responseExample = normalizeJson(dto.getResponseExample(), "返回示例");
        String errorCodes = normalizeJson(dto.getErrorCodes(), "错误码");

        adminApiDocMapper.updateHuman(id, apiName, isPublic, responseExample, errorCodes);
        return detail(doc.getId());
    }

    private ApiItemVO toItem(AdminApiDoc doc) {
        ApiItemVO item = new ApiItemVO();
        item.setKey("api:" + doc.getId());
        item.setId(doc.getId());
        String title = doc.getApiName();
        if (title == null || title.isBlank()) {
            title = doc.getSummary();
        }
        if (title == null || title.isBlank()) {
            title = doc.getPath();
        }
        item.setTitle(title);
        item.setMethod(doc.getHttpMethod());
        item.setPath(doc.getPath());
        item.setSummary(doc.getSummary());
        item.setMissing(doc.getMissing() != null && doc.getMissing() == 1);
        return item;
    }

    /** JSON 字段校验：空串存 NULL，非法 JSON 直接 400 */
    private String normalizeJson(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(value);
            return value;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + "不是合法的 JSON");
        }
    }
}
