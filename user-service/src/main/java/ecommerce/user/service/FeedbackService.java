package ecommerce.user.service;

import ecommerce.user.dto.CreateFeedbackDTO;

/**
 * 意见反馈 Service。
 */
public interface FeedbackService {

    /** 提交反馈：校验类型/内容/截图后落库，返回反馈 ID */
    Long submit(CreateFeedbackDTO dto);
}
