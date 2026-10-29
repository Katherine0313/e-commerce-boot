package ecommerce.user.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.user.dto.CreateFeedbackDTO;
import ecommerce.user.entity.Feedback;
import ecommerce.user.mapper.FeedbackMapper;
import ecommerce.user.service.FeedbackService;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 意见反馈 Service 实现。
 * <p>
 * 用户/店铺从 UserContext（JWT）取；图片只接受本服务上传接口产出的 /image/feedback/ 相对路径，
 * 避免把任意外链写进库里。
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    /** 允许的反馈类型 code */
    private static final Set<String> ALLOWED_TYPES = Set.of("suggestion", "product", "order", "other");
    private static final int MAX_IMAGES = 6;
    private static final int MIN_CONTENT_LENGTH = 5;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final FeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper, ObjectMapper objectMapper) {
        this.feedbackMapper = feedbackMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long submit(CreateFeedbackDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        // 类型：不传默认 other，传了必须是白名单内的 code
        String type = dto.getType() == null || dto.getType().isBlank() ? "other" : dto.getType().trim();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "反馈类型不合法");
        }
        // 内容：去首尾空格后 5~1000 字
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.length() < MIN_CONTENT_LENGTH || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "反馈内容需 " + MIN_CONTENT_LENGTH + "~" + MAX_CONTENT_LENGTH + " 个字");
        }
        // 截图：最多 6 张，且必须来自本服务的 feedback 上传目录
        List<String> images = dto.getImages() == null ? List.of() : dto.getImages();
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最多上传 " + MAX_IMAGES + " 张截图");
        }
        for (String image : images) {
            if (image == null || !image.startsWith("/image/feedback/")) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "截图地址不合法");
            }
        }
        // 联系方式选填，长度与库字段对齐
        String contact = dto.getContact() == null ? null : dto.getContact().trim();
        if (contact != null && contact.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "联系方式不能超过 64 个字符");
        }
        if (contact != null && contact.isEmpty()) {
            contact = null;
        }

        LocalDateTime now = LocalDateTime.now();
        Feedback feedback = Feedback.builder()
                .userId(userId)
                .type(type)
                .content(content)
                .images(toJson(images))
                .contact(contact)
                .status(0)
                .isDeleted(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        feedbackMapper.insert(feedback);
        return feedback.getId();
    }

    /** 截图数组 → JSON 字符串（空数组也存 []，与评价表一致） */
    private String toJson(List<String> images) {
        return objectMapper.writeValueAsString(images);
    }
}
