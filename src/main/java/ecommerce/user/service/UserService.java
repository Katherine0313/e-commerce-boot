package ecommerce.user.service;

import ecommerce.user.vo.UserProfileVO;
import ecommerce.user.vo.WxLoginVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    WxLoginVO login(String code);

    /**
     * 获取用户资料：头像、昵称、基础信息一次返回
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 上传头像：保存文件并更新 user.avatar，返回相对路径（如 /upload/avatar/xxx.jpg）
     */
    String uploadAvatar(Long userId, MultipartFile file);

}
