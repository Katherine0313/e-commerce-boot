package ecommerce.user.service.impl;

import ecommerce.user.vo.UserProfileVO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ecommerce.exception.BusinessException;
import ecommerce.user.entity.User;
import ecommerce.user.mapper.UserMapper;
import ecommerce.user.service.UserService;
import ecommerce.user.vo.WxLoginVO;
import ecommerce.properties.WeChatProperties;
import ecommerce.utils.HttpClientUtil;
import ecommerce.utils.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    private final WeChatProperties weChatProperties;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;


    public UserServiceImpl(WeChatProperties weChatProperties, HttpClientUtil httpClientUtil,
                           ObjectMapper objectMapper, UserMapper userMapper, JwtUtil jwtUtil) {
        this.weChatProperties = weChatProperties;
        this.httpClientUtil = httpClientUtil;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;

    }

    @Override
    public WxLoginVO login(String code) {
        // 0. 校验 code：wx.login() 拿到的临时凭证不能为空
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "登录凭证 code 不能为空");
        }
        // 1. code 换 openid
        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");
        String respBody = httpClientUtil.doGet("https://api.weixin.qq.com/sns/jscode2session", map);
        // 2. 解析微信返回的 openid，code 无效时微信会返回 errcode 而没有 openid
        JsonNode openidNode = objectMapper.readTree(respBody).get("openid");
        if (openidNode == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "登录凭证 code 无效或已过期");
        }
        String openid = openidNode.stringValue();
        // 3. 根据 openid 查用户，查不到（首次登录）就自动注册
        User user = userMapper.findByOpenid(openid);
        if (user == null) {
            user = User.builder().openid(openid).build();
            userMapper.insertUser(user); // insertUser 会自动把自增 id 回填到 user.id
        }
        // 4. 生成 token 返回给调用方
        return WxLoginVO.builder()
                .token(jwtUtil.generateUserToken(user.getId()))
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .build();
    }

    //查询用户，用户不存在就报错；查到了就把实体数据组装成 VO 返回，给前端展示个人资料（包含头像）。
    @Override
    public UserProfileVO getProfile(Long userId) {
        // 1. 根据用户id查询该用户
        User user = userMapper.findById(userId);
        // 2.如果查不到用户，抛出业务异常
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        // 3.构建vo对象，最终返回给前端
        return UserProfileVO.builder()
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .sex(user.getSex())
                .idNumber(user.getIdNumber())
                .createTime(user.getCreateTime())
                .build();
    }


    /**
     * 头像上传目录（相对运行目录），与 WebMvcConfig 的静态映射保持一致
     */

    private static final String UPLOAD_DIR = "upload/avatar/";

    /**
     * 允许上传的图片类型 → 落盘后缀
     * <p>
     * 用白名单而不是 contentType.startsWith("image/")：后者会放行 image/svg+xml，
     * 而 SVG 能内嵌脚本，配上 /upload/** 的静态直接访问就是 XSS；
     * 另外白名单直接给出规范后缀，避免从 Content-Type 里切出 "svg+xml" 这种非法扩展名。
     */
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        // 只收白名单内的图片类型；Content-Type 由客户端提供，大小写和参数都可能不规范，先归一化
        String contentType = normalizeContentType(file.getContentType());
        String suffix = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType);
        if (suffix == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只支持 png / jpg / gif / webp 格式的图片");
        }
        // 上传前先记下旧头像路径，等新文件保存成功后再删旧文件，避免反复上传堆满磁盘
        User user = userMapper.findById(userId);
        String oldAvatar = user == null ? null : user.getAvatar();

        // 保存文件：upload/avatar/{userId}_{时间戳}.{后缀}
        String filename = userId + "_" + System.currentTimeMillis() + "." + suffix;
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            file.transferTo(new File(dir, filename).toPath());
        } catch (IOException e) {
            throw new BusinessException("头像保存失败");
        }
        // 库里存相对路径，完整 URL 由 Controller 按当前请求域名拼
        String relativeUrl = "/upload/avatar/" + filename;
        userMapper.updateAvatar(userId, relativeUrl);
        // 写库成功后删旧文件（只删本地 /upload/avatar/ 下的，外部 URL 不碰）
        deleteOldAvatarFile(oldAvatar);
        return relativeUrl;
    }
    // 归一化 Content-Type：统一小写、去掉「;」后面的参数（如 charset），便于按白名单比对；空值原样返回
    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        return semicolon > 0 ? normalized.substring(0, semicolon).trim() : normalized;
    }

    // 删除旧头像文件：只处理 /upload/avatar/ 开头的本地相对路径，其余忽略
    private void deleteOldAvatarFile(String oldAvatar) {
        if (oldAvatar == null || !oldAvatar.startsWith("/upload/avatar/")) {
            return;
        }
        String oldName = oldAvatar.substring("/upload/avatar/".length());
        if (oldName.isBlank()) {
            return;
        }
        File oldFile = new File(UPLOAD_DIR, oldName);
        if (oldFile.exists()) {
            oldFile.delete();
        }
    }
}
