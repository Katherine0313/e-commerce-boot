package ecommerce.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.user.dto.AdminMemberLevelDTO;
import ecommerce.user.entity.MemberLevel;
import ecommerce.user.entity.User;
import ecommerce.user.mapper.MemberLevelMapper;
import ecommerce.user.mapper.MemberMapper;
import ecommerce.user.mapper.UserMapper;
import ecommerce.user.service.MemberService;
import ecommerce.user.vo.AdminMemberVO;
import ecommerce.user.vo.MemberLevelVO;
import ecommerce.user.vo.MemberProfileVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 会员实现：成长值按订单实时重算，等级只升不降。
 */
@Service
public class MemberServiceImpl implements MemberService {

    private final UserMapper userMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final MemberMapper memberMapper;

    public MemberServiceImpl(UserMapper userMapper, MemberLevelMapper memberLevelMapper, MemberMapper memberMapper) {
        this.userMapper = userMapper;
        this.memberLevelMapper = memberLevelMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public MemberProfileVO getProfile() {
        return refreshAndBuild(UserContext.getUserId());
    }

    @Override
    public void refreshMember(Long userId) {
        refreshAndBuild(userId);
    }

    /** 重算指定用户的成长值/等级并回写，返回最新档案 */
    private MemberProfileVO refreshAndBuild(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在或已注销");
        }
        List<MemberLevel> levels = allLevels();

        // 成长值 = 已支付实付 - 已退款，1 元 = 1 成长值（向下取整）
        Map<String, Object> stats = memberMapper.paidStats(userId);
        BigDecimal paid = toDecimal(stats == null ? null : stats.get("paid_amount"));
        int paidCount = toInt(stats == null ? null : stats.get("paid_count"));
        BigDecimal refunded = memberMapper.sumRefunded(userId);
        BigDecimal netPaid = paid.subtract(refunded == null ? BigDecimal.ZERO : refunded);
        if (netPaid.signum() < 0) {
            netPaid = BigDecimal.ZERO;
        }
        int growth = netPaid.setScale(0, RoundingMode.FLOOR).intValue();

        MemberLevel current = resolveLevel(levels, growth);
        MemberLevel next = resolveNextLevel(levels, growth);

        // 快照落后时回写，避免每次请求都重复更新
        if (!Objects.equals(user.getMemberLevelId(), current.getId())
                || user.getGrowthValue() == null || user.getGrowthValue() != growth
                || user.getTotalPaidAmount() == null || user.getTotalPaidAmount().compareTo(netPaid) != 0
                || user.getPaidOrderCount() == null || user.getPaidOrderCount() != paidCount) {
            memberMapper.updateMemberStats(userId, current.getId(), growth, netPaid, paidCount,
                    LocalDateTime.now());
        }

        return MemberProfileVO.builder()
                .userId(userId)
                .name(user.getName())
                .avatar(user.getAvatar())
                .levelId(current.getId())
                .levelName(current.getName())
                .growthValue(growth)
                .benefits(current.getBenefits())
                .totalPaidAmount(netPaid)
                .paidOrderCount(paidCount)
                .currentThreshold(current.getGrowthThreshold())
                .nextLevelId(next == null ? null : next.getId())
                .nextLevelName(next == null ? null : next.getName())
                .nextLevelThreshold(next == null ? null : next.getGrowthThreshold())
                .growthToNext(next == null ? 0 : Math.max(0, next.getGrowthThreshold() - growth))
                .build();
    }

    @Override
    public List<MemberLevelVO> listLevels() {
        return allLevels().stream().map(this::toLevelVO).toList();
    }

    @Override
    public PageVO<AdminMemberVO> listMembers(int page, int pageSize, Long levelId, String keyword, String status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String safeStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (safeStatus != null && !Set.of("NORMAL", "DELETED").contains(safeStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "会员状态不合法");
        }
        int offset = (safePage - 1) * safePageSize;
        List<AdminMemberVO> list = memberMapper.listMembers(levelId, safeKeyword, safeStatus, offset, safePageSize);
        long total = memberMapper.countMembers(levelId, safeKeyword, safeStatus);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public AdminMemberVO getMember(Long id) {
        AdminMemberVO member = memberMapper.findMemberById(id);
        if (member == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会员不存在");
        }
        return member;
    }

    @Override
    public void updateLevel(Long id, AdminMemberLevelDTO dto) {
        MemberLevel level = memberLevelMapper.selectById(id);
        if (level == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会员等级不存在");
        }
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "等级名称不能为空");
        }
        if (dto.getGrowthThreshold() == null || dto.getGrowthThreshold() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "成长值门槛不能为负数");
        }
        long duplicate = memberLevelMapper.selectCount(new QueryWrapper<MemberLevel>()
                .eq("growth_threshold", dto.getGrowthThreshold())
                .ne("id", id));
        if (duplicate > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "成长值门槛与其他等级重复");
        }
        level.setName(dto.getName().trim());
        level.setGrowthThreshold(dto.getGrowthThreshold());
        level.setBenefits(dto.getBenefits());
        level.setSortOrder(dto.getSortOrder() == null ? level.getSortOrder() : dto.getSortOrder());
        level.setUpdateTime(LocalDateTime.now());
        memberLevelMapper.updateById(level);
    }

    // ==================== 内部方法 ====================

    private List<MemberLevel> allLevels() {
        return memberLevelMapper.selectList(new QueryWrapper<MemberLevel>()
                .orderByAsc("growth_threshold"));
    }

    /** 成长值达到的最高等级；没有匹配时回退到最低等级 */
    private MemberLevel resolveLevel(List<MemberLevel> levels, int growth) {
        MemberLevel matched = null;
        for (MemberLevel level : levels) {
            if (level.getGrowthThreshold() != null && level.getGrowthThreshold() <= growth) {
                matched = level;
            }
        }
        if (matched != null) {
            return matched;
        }
        if (levels.isEmpty()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "会员等级未配置");
        }
        return levels.get(0);
    }

    private MemberLevel resolveNextLevel(List<MemberLevel> levels, int growth) {
        for (MemberLevel level : levels) {
            if (level.getGrowthThreshold() != null && level.getGrowthThreshold() > growth) {
                return level;
            }
        }
        return null;
    }

    private MemberLevelVO toLevelVO(MemberLevel level) {
        return MemberLevelVO.builder()
                .id(level.getId())
                .name(level.getName())
                .growthThreshold(level.getGrowthThreshold())
                .benefits(level.getBenefits())
                .sortOrder(level.getSortOrder())
                .build();
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }
}
