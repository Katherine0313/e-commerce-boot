package ecommerce.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ecommerce.user.vo.UserProfileVO;
import ecommerce.user.vo.UserStatisticsVO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ecommerce.exception.BusinessException;
import ecommerce.user.entity.User;
import ecommerce.user.mapper.CartMapper;
import ecommerce.user.mapper.UserMapper;
import ecommerce.user.service.UserService;
import ecommerce.user.vo.WxLoginVO;
import ecommerce.properties.WeChatProperties;
import ecommerce.utils.HttpClientUtil;
import ecommerce.utils.JwtUtil;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    private final WeChatProperties weChatProperties;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final CartMapper cartMapper;
    private final JwtUtil jwtUtil;

    /** 微信 access_token 内存缓存：全局一份，2 小时有效，提前 5 分钟刷新，避免临界点拿到失效 token */
    private volatile String cachedAccessToken;
    private volatile long accessTokenExpireAt;


    public UserServiceImpl(WeChatProperties weChatProperties, HttpClientUtil httpClientUtil,
                           ObjectMapper objectMapper, UserMapper userMapper, CartMapper cartMapper,
                           JwtUtil jwtUtil) {
        this.weChatProperties = weChatProperties;
        this.httpClientUtil = httpClientUtil;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.cartMapper = cartMapper;
        this.jwtUtil = jwtUtil;

    }

    @Override
    public WxLoginVO login(String code, Long shopId) {
        // 0. 校验 code：wx.login() 拿到的临时凭证不能为空
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "登录凭证 code 不能为空");
        }
        // shopId 默认模板店（兼容旧调用不传）
        Long sid = shopId == null ? 1L : shopId;
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
        // 3. 按 openid + shopId 查用户，查不到（首次登录）就自动注册
        // 不加 is_deleted 过滤（注销用户 openid 仍占 uk_shop_openid，查不到会撞键）
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid).eq(User::getShopId, sid));
        if (user == null) {
            user = User.builder().openid(openid).shopId(sid).build();
            userMapper.insert(user); // BaseMapper.insert 自动回填自增 id
        } else if (isDeleted(user)) {
            // 已注销用户禁止再登录（新注册用户 is_deleted 走库默认 0，不会走到这）
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已注销");
        }
        // 4. 生成 token 返回给调用方（签 shopId，下游按店铺隔离）
        return WxLoginVO.builder()
                .token(jwtUtil.generateUserToken(user.getId(), sid))
                .id(user.getId())
                .avatar(user.getAvatar())
                .build();
    }

    // 绑定手机号：用 getPhoneNumber 按钮授权拿到的 code 换手机号并写回 user.phone，返回手机号
    @Override
    public String bindPhone(Long userId, String code) {
        // 1. 手机号授权 code 不能为空
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号授权 code 不能为空");
        }
        // 2. 用户必须存在且未注销
        findActiveUser(userId);
        // 3. code 换手机号：先取 access_token，再调 getuserphonenumber（POST JSON）
        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + accessToken;
        String respBody = httpClientUtil.doPostJson(url, objectMapper.writeValueAsString(Map.of("code", code)));
        JsonNode node = objectMapper.readTree(respBody);
        // 4. 微信 errcode 非 0 即失败（code 无效/过期、无权限等）
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "获取手机号失败：" + node.path("errmsg").asText(""));
        }
        // 5. 取纯手机号（不含国家码）写库
        String phone = node.path("phone_info").path("purePhoneNumber").asText(null);
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("微信未返回手机号");
        }
        // updateById 只更新非 null 字段，update_time 由 MyMetaObjectHandler 自动填充
        userMapper.updateById(User.builder().id(userId).phone(phone).build());
        return phone;
    }

    // 获取微信 access_token：内存缓存，未过期直接用，过期（或首次）调微信 token 接口刷新
    private String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < accessTokenExpireAt) {
            return cachedAccessToken;
        }
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "client_credential");
        params.put("appid", weChatProperties.getAppid());
        params.put("secret", weChatProperties.getSecret());
        String respBody = httpClientUtil.doGet("https://api.weixin.qq.com/cgi-bin/token", params);
        JsonNode node = objectMapper.readTree(respBody);
        String token = node.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new BusinessException("获取微信 access_token 失败：" + respBody);
        }
        // expires_in 单位秒，提前 5 分钟过期，避免临界点拿到失效 token
        long expiresIn = node.path("expires_in").asLong(7200);
        cachedAccessToken = token;
        accessTokenExpireAt = now + (expiresIn - 300) * 1000;
        return token;
    }

    //查询用户，用户不存在就报错；查到了就把实体数据组装成 VO 返回，给前端展示个人资料（包含头像）。
    @Override
    public UserProfileVO getProfile(Long userId) {
        // 1. 取用户：不存在或已注销都视为不可用（令牌可能还在有效期内，靠这里兜住）
        User user = findActiveUser(userId);
        // 2.构建vo对象，最终返回给前端
        return UserProfileVO.builder()
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .sex(user.getSex())
                .createTime(user.getCreateTime())
                .build();
    }

    // 设置昵称：校验后写入 user.name，返回去除首尾空格后的昵称
    @Override
    public String updateName(Long userId, String name) {
        // 1. 昵称不能为空（null 或纯空格都不行）
        if (name == null || name.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "昵称不能为空");
        }
        // 2. 去掉首尾空格，避免脏数据入库
        String trimmed = name.trim();
        // 3. 长度上限：与库 name varchar(32) 对齐，避免超长写库报错
        if (trimmed.length() > 32) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "昵称不能超过 32 个字符");
        }
        // 4. 用户必须存在且未注销（登录态有效但账号可能已注销），否则更新影响 0 行
        findActiveUser(userId);
        // 5. 写库，返回最终入库的昵称
        userMapper.updateById(User.builder().id(userId).name(trimmed).build());
        return trimmed;
    }

    // 注销账号：软删用户 + 清购物车，同一事务（任一步失败一起回滚）
    @Override
    @Transactional
    public void deactivate(Long userId) {
        // 1. 用户必须存在且未注销，否则抛 401（重复注销在这里就被挡住）
        findActiveUser(userId);
        // 2. 置 is_deleted = 1（不物理删：orders.user_id 是 RESTRICT，订单要保留）
        // 软删：is_deleted=0 条件保证重复注销不重复计数；传 new User() 触发 updateFill 填 update_time
        userMapper.update(new User(), new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).eq(User::getIsDeleted, 0)
                .set(User::getIsDeleted, 1));
        // 3. 清空该用户购物车（cart 同库直连删；订单/收货地址不动，地址快照已冗余进订单）
        cartMapper.deleteByUser(userId);
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
        String oldAvatar = findActiveUser(userId).getAvatar();

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
        userMapper.updateById(User.builder().id(userId).avatar(relativeUrl).build());
        // 写库成功后删旧文件（只删本地 /upload/avatar/ 下的，外部 URL 不碰）
        deleteOldAvatarFile(oldAvatar);
        return relativeUrl;
    }
    // 取「可用」用户：不存在或已注销都抛 401，供资料/昵称/头像等按 id 操作的入口复用
    private User findActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || isDeleted(user)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    // 是否已注销（is_deleted = 1）
    private boolean isDeleted(User user) {
        return Integer.valueOf(1).equals(user.getIsDeleted());
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

    @Override
    public UserStatisticsVO getUserStatistics() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            shopId = 1L; // 模板店默认
        }
        // 近 12 个月（含本月）起始日期：往前推 11 个月的 1 号
        String startDate = LocalDate.now().minusMonths(11).withDayOfMonth(1).toString();
        List<Map<String, Object>> rows = userMapper.countMonthlyNewUsers(shopId, startDate);

        // 行转 map（month -> cnt），缺失月份补 0
        Map<String, Long> cntMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            cntMap.put(String.valueOf(row.get("month")), ((Number) row.get("cnt")).longValue());
        }
        LocalDate firstMonth = LocalDate.now().minusMonths(11).withDayOfMonth(1);
        List<String> months = new ArrayList<>();
        List<Long> newUsers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String month = firstMonth.plusMonths(i).toString().substring(0, 7);
            months.add(month);
            newUsers.add(cntMap.getOrDefault(month, 0L));
        }
        return UserStatisticsVO.builder()
                .months(months)
                .newUsers(newUsers)
                .totalUsers(userMapper.countTotalUsers(shopId))
                .build();
    }
}
