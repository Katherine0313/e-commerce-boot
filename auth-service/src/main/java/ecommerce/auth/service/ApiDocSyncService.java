package ecommerce.auth.service;

import ecommerce.auth.entity.AdminApiDoc;
import ecommerce.auth.mapper.AdminApiDocMapper;
import ecommerce.auth.vo.ApiSyncResultVO;
import ecommerce.exception.BusinessException;
import ecommerce.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从网关聚合的 springdoc OpenAPI 同步接口清单。
 * <p>
 * 网关已暴露 /v3/api-docs/swagger-config，其中 urls[] 指向各服务文档。
 * 本服务逐个拉取并 upsert；本次未出现的接口只标记 missing，不直接删除。
 */
@Slf4j
@Service
public class ApiDocSyncService {

    private static final String[] METHODS = {"get", "post", "put", "delete", "patch", "head", "options"};

    private final AdminApiDocMapper adminApiDocMapper;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;

    @Value("${system.api-doc.gateway-url:http://127.0.0.1:8080}")
    private String gatewayUrl;

    public ApiDocSyncService(AdminApiDocMapper adminApiDocMapper,
                             HttpClientUtil httpClientUtil,
                             ObjectMapper objectMapper) {
        this.adminApiDocMapper = adminApiDocMapper;
        this.httpClientUtil = httpClientUtil;
        this.objectMapper = objectMapper;
    }

    public ApiSyncResultVO sync() {
        JsonNode config = readJson(gatewayUrl + "/v3/api-docs/swagger-config");
        JsonNode urls = config.path("urls");
        if (!urls.isArray() || urls.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "网关未开启接口文档聚合（/v3/api-docs/swagger-config 为空）");
        }

        Set<String> existingKeys = loadExistingKeys();
        ApiSyncResultVO result = new ApiSyncResultVO();
        result.setAdded(0);
        result.setUpdated(0);
        result.setTotal(0);
        result.setServices(new ArrayList<>());
        result.setErrors(new ArrayList<>());

        for (JsonNode entry : urls) {
            String url = entry.path("url").asText("");
            String service = deriveService(url, entry.path("name").asText(""));
            if (url.isBlank()) {
                continue;
            }
            try {
                JsonNode root = readJson(absoluteUrl(url));
                adminApiDocMapper.markMissingByService(service);
                syncService(service, root, existingKeys, result);
                result.getServices().add(service);
            } catch (Exception e) {
                log.warn("同步接口文档失败: service={}, err={}", service, e.getMessage());
                result.getErrors().add(service + ": " + e.getMessage());
            }
        }

        result.setMissing(adminApiDocMapper.countMissing());
        result.setSyncedAt(LocalDateTime.now());
        return result;
    }

    private void syncService(String service, JsonNode root, Set<String> existingKeys, ApiSyncResultVO result) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.properties().iterator();
        while (pathIterator.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIterator.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            for (String method : METHODS) {
                JsonNode operation = pathItem.get(method);
                if (operation == null || !operation.isObject()) {
                    continue;
                }
                AdminApiDoc doc = buildDoc(service, path, method, operation, root, pathItem);
                String key = service + "|" + doc.getHttpMethod() + "|" + doc.getPath();
                if (existingKeys.contains(key)) {
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    result.setAdded(result.getAdded() + 1);
                    existingKeys.add(key);
                }
                result.setTotal(result.getTotal() + 1);
                adminApiDocMapper.upsert(doc);
            }
        }
    }

    private AdminApiDoc buildDoc(String service, String path, String method,
                                 JsonNode operation, JsonNode root, JsonNode pathItem) {
        AdminApiDoc doc = new AdminApiDoc();
        doc.setServiceName(service);
        doc.setGroupName(resolveGroup(path, operation));
        doc.setHttpMethod(method.toUpperCase());
        doc.setPath(path);
        doc.setSummary(text(operation, "summary"));
        doc.setDescription(text(operation, "description"));
        doc.setRequirePerm(operation.path("x-require-perm").asText(null));
        doc.setIsPublic(0);
        doc.setMissing(0);
        doc.setApiName(null);
        doc.setResponseExample(null);
        doc.setErrorCodes(null);

        // query / path / header 参数
        ArrayNode queryParams = objectMapper.createArrayNode();
        ArrayNode headerParams = objectMapper.createArrayNode();
        JsonNode parameters = operation.path("parameters");
        if (parameters.isArray()) {
            for (JsonNode parameter : parameters) {
                String in = parameter.path("in").asText("");
                if ("body".equals(in)) {
                    doc.setBodyParams(json(resolveSchema(root, parameter.path("schema"), 0)));
                    continue;
                }
                if ("header".equals(in)) {
                    addParameter(headerParams, parameter);
                } else if ("query".equals(in) || "path".equals(in) || "cookie".equals(in)) {
                    addParameter(queryParams, parameter);
                }
            }
        }

        // OpenAPI 3 请求体
        JsonNode requestBody = operation.path("requestBody");
        if (requestBody.isObject()) {
            JsonNode schema = firstSchema(requestBody);
            doc.setBodyParams(json(resolveSchema(root, schema, 0)));
        }

        // 2xx 响应
        JsonNode schema = firstResponseSchema(operation.path("responses"));
        if (schema != null) {
            doc.setResponseParams(json(resolveSchema(root, schema, 0)));
        }

        doc.setQueryParams(json(queryParams));
        doc.setHeaderParams(json(headerParams));
        return doc;
    }

    private void addParameter(ArrayNode array, JsonNode parameter) {
        ObjectNode item = array.addObject();
        item.put("name", parameter.path("name").asText(""));
        item.put("in", parameter.path("in").asText("query"));
        item.put("type", typeOf(parameter.path("schema")));
        item.put("required", parameter.path("required").asBoolean(false));
        item.put("description", parameter.path("description").asText(""));
    }

    private JsonNode firstSchema(JsonNode requestBody) {
        JsonNode content = requestBody.path("content");
        JsonNode media = content.path("application/json");
        if (media.isMissingNode() && content.isObject()) {
            Iterator<JsonNode> iterator = content.values().iterator();
            if (iterator.hasNext()) {
                media = iterator.next();
            }
        }
        return media.path("schema");
    }

    private JsonNode firstResponseSchema(JsonNode responses) {
        if (!responses.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = responses.properties().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (!entry.getKey().startsWith("2")) {
                continue;
            }
            JsonNode response = entry.getValue();
            JsonNode content = response.path("content");
            if (content.isObject()) {
                JsonNode media = content.path("application/json");
                if (media.isMissingNode()) {
                    Iterator<JsonNode> values = content.values().iterator();
                    if (values.hasNext()) {
                        media = values.next();
                    }
                }
                JsonNode schema = media.path("schema");
                if (!schema.isMissingNode()) {
                    return schema;
                }
            }
            JsonNode schema = response.path("schema");
            if (!schema.isMissingNode()) {
                return schema;
            }
        }
        return null;
    }

    /** 解析 $ref（最多 4 层），便于前端直接渲染字段 */
    private JsonNode resolveSchema(JsonNode root, JsonNode schema, int depth) {
        if (schema == null || schema.isNull() || schema.isMissingNode() || depth > 4) {
            return schema;
        }
        String ref = schema.path("$ref").asText("");
        if (ref.startsWith("#/components/schemas/")) {
            String name = ref.substring("#/components/schemas/".length());
            JsonNode target = root.path("components").path("schemas").path(name);
            if (!target.isMissingNode()) {
                ObjectNode copy = (ObjectNode) target.deepCopy();
                copy.put("title", name);
                return resolveSchema(root, copy, depth + 1);
            }
        }
        if (schema.isArray()) {
            ArrayNode copy = (ArrayNode) schema.deepCopy();
            for (int i = 0; i < copy.size(); i++) {
                copy.set(i, resolveSchema(root, copy.get(i), depth + 1));
            }
            return copy;
        }
        if (schema.isObject()) {
            ObjectNode copy = (ObjectNode) schema.deepCopy();
            JsonNode properties = copy.get("properties");
            if (properties != null && properties.isObject()) {
                ObjectNode propertyNode = (ObjectNode) properties;
                for (String name : new ArrayList<>(propertyNode.propertyNames())) {
                    propertyNode.set(name, resolveSchema(root, propertyNode.get(name), depth + 1));
                }
            }
            if (copy.has("items")) {
                copy.set("items", resolveSchema(root, copy.get("items"), depth + 1));
            }
            return copy;
        }
        return schema;
    }

    private String resolveGroup(String path, JsonNode operation) {
        JsonNode tags = operation.path("tags");
        if (tags.isArray() && !tags.isEmpty()) {
            String tag = tags.get(0).asText("");
            if (!tag.isBlank()) {
                return tag;
            }
        }
        String[] segments = path.split("/");
        String group = "default";
        for (int i = 0; i < segments.length; i++) {
            if ("api".equals(segments[i]) && i + 2 < segments.length) {
                group = segments[i + 2];
                break;
            }
        }
        return group;
    }

    private String typeOf(JsonNode schema) {
        if (schema == null || schema.isNull() || schema.isMissingNode()) {
            return "any";
        }
        JsonNode type = schema.get("type");
        if (type != null && !type.isNull()) {
            return type.asText();
        }
        String ref = schema.path("$ref").asText("");
        if (!ref.isBlank()) {
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        if (schema.isObject() && schema.has("items")) {
            return "array<" + typeOf(schema.get("items")) + ">";
        }
        return "object";
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String json(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.writeValueAsString(node);
    }

    private JsonNode readJson(String url) {
        try {
            String body = httpClientUtil.get(url);
            return objectMapper.readTree(body);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "读取 OpenAPI 失败: " + url + "，" + e.getMessage());
        }
    }

    private String absoluteUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return gatewayUrl + (url.startsWith("/") ? url : "/" + url);
    }

    /** url 形如 /auth-service/v3/api-docs 或绝对地址，取服务名 */
    private String deriveService(String url, String name) {
        String clean = url.replaceAll("^https?://[^/]+", "").replaceAll("^/+", "");
        int slash = clean.indexOf('/');
        String service = slash > 0 ? clean.substring(0, slash) : clean;
        service = service.replaceAll("[^A-Za-z0-9_-]", "");
        if (service.isBlank()) {
            service = name.isBlank() ? "default" : name;
        }
        return service;
    }

    private Set<String> loadExistingKeys() {
        List<AdminApiDoc> keys = adminApiDocMapper.listKeys();
        Set<String> result = new HashSet<>(keys.size() * 2);
        for (AdminApiDoc doc : keys) {
            result.add(doc.getServiceName() + "|" + doc.getHttpMethod() + "|" + doc.getPath());
        }
        return result;
    }
}
