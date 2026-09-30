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

    /**
     * 设置昵称：校验（非空、长度）后更新 user.name，返回去除首尾空格后的昵称
     */
    String updateName(Long userId, String name);

}
