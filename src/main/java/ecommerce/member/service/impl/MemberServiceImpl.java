package ecommerce.member.service.impl;

import ecommerce.member.vo.MemberProfileVO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ecommerce.exception.BusinessException;
import ecommerce.member.entity.Member;
import ecommerce.member.mapper.MemberMapper;
import ecommerce.member.service.MemberService;
import ecommerce.member.vo.WxLoginVO;
import ecommerce.properties.WeChatProperties;
import ecommerce.utils.HttpClientUtil;
import ecommerce.utils.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class MemberServiceImpl implements MemberService {
    private final WeChatProperties weChatProperties;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;
    private final MemberMapper memberMapper;
    private final JwtUtil jwtUtil;


    public MemberServiceImpl(WeChatProperties weChatProperties, HttpClientUtil httpClientUtil,
                             ObjectMapper objectMapper, MemberMapper memberMapper, JwtUtil jwtUtil) {
        this.weChatProperties = weChatProperties;
        this.httpClientUtil = httpClientUtil;
        this.objectMapper = objectMapper;
        this.memberMapper = memberMapper;
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
        Member member = memberMapper.findByOpenid(openid);
        if (member == null) {
            member = Member.builder().openid(openid).build();
            memberMapper.insertMember(member); // insertMember 会自动把自增 id 回填到 member.id
        }
        // 4. 生成 token 返回给调用方
        return WxLoginVO.builder()
                .token(jwtUtil.generateMemberToken(member.getId()))
                .id(member.getId())
                .name(member.getName())
                .avatar(member.getAvatar())
                .build();
    }

    @Override
    public MemberProfileVO getProfile(Long userId) {
        Member member = memberMapper.findById(userId);
        if (member == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        return MemberProfileVO.builder()
                .id(member.getId())
                .name(member.getName())
                .avatar(member.getAvatar())
                .phone(member.getPhone())
                .sex(member.getSex())
                .idNumber(member.getIdNumber())
                .createTime(member.getCreateTime())
                .build();
    }

    /** 头像上传目录（相对运行目录），与 WebMvcConfig 的静态映射保持一致 */
    private static final String UPLOAD_DIR = "upload/avatar/";

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        // 只收图片
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只支持图片文件");
        }
        // 上传前先记下旧头像路径，等新文件保存成功后再删旧文件，避免反复上传堆满磁盘
        Member member = memberMapper.findById(userId);
        String oldAvatar = member == null ? null : member.getAvatar();

        // 保存文件：upload/avatar/{userId}_{时间戳}.{后缀}
        String suffix = contentType.substring(contentType.indexOf('/') + 1);
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
        memberMapper.updateAvatar(userId, relativeUrl);
        // 写库成功后删旧文件（只删本地 /upload/avatar/ 下的，外部 URL 不碰）
        deleteOldAvatarFile(oldAvatar);
        return relativeUrl;
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
