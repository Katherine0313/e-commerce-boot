package ecommerce.user.service;

import ecommerce.user.vo.UserProfileVO;
import ecommerce.user.vo.UserStatisticsVO;
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

    /**
     * 绑定手机号：用 getPhoneNumber 授权 code 换手机号并写回 user.phone，返回手机号
     */
    String bindPhone(Long userId, String code);

    /**
     * 注销账号：软删除用户（is_deleted=1）并清空其购物车，两者同一事务
     */
    void deactivate(Long userId);

    /**
     * 用户增长统计：按 period（yesterday/week/month/year）返回对应时间桶的新增用户数、
     * 累计用户数与用户总量（后台工作台用）
     */
    UserStatisticsVO getUserStatistics(String period);

}
