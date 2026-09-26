package ecommerce.member.service;

import ecommerce.member.vo.MemberProfileVO;
import ecommerce.member.vo.WxLoginVO;
import org.springframework.web.multipart.MultipartFile;

public interface MemberService {
    WxLoginVO login(String code);

    /**
     * 获取用户资料：头像、昵称、基础信息一次返回
     */
    MemberProfileVO getProfile(Long userId);

    /**
     * 上传头像：保存文件并更新 member.avatar，返回相对路径（如 /upload/avatar/xxx.jpg）
     */
    String uploadAvatar(Long userId, MultipartFile file);
}
