package ecommerce.user.service;

import ecommerce.common.PageVO;
import ecommerce.user.dto.AdminMemberLevelDTO;
import ecommerce.user.vo.AdminMemberVO;
import ecommerce.user.vo.MemberLevelVO;
import ecommerce.user.vo.MemberProfileVO;

import java.util.List;

/**
 * 会员 Service：等级、成长值、会员中心、后台会员列表。
 */
public interface MemberService {

    /** 当前用户会员档案（会顺带按订单重算成长值和等级） */
    MemberProfileVO getProfile();

    /** 等级列表（C 端展示） */
    List<MemberLevelVO> listLevels();

    /** 后台会员列表；status：NORMAL 正常 / DELETED 已注销 / null 全部 */
    PageVO<AdminMemberVO> listMembers(int page, int pageSize, Long levelId, String keyword, String status);

    /** 后台会员详情（含已注销用户，只读） */
    AdminMemberVO getMember(Long id);

    /** 后台编辑等级门槛/名称/权益 */
    void updateLevel(Long id, AdminMemberLevelDTO dto);

    /** 后台手动刷新某个用户的成长值与等级 */
    void refreshMember(Long userId);
}
