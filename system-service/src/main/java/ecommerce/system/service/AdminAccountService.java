package ecommerce.system.service;

import ecommerce.system.dto.AdminAccountDTO;
import ecommerce.system.vo.AdminAccountVO;
import ecommerce.common.PageVO;

/**
 * 后台管理员账号管理。
 */
public interface AdminAccountService {

    PageVO<AdminAccountVO> list(int page, int pageSize, String keyword, Integer status);

    AdminAccountVO get(Long id);

    Long create(AdminAccountDTO dto);

    void update(Long id, AdminAccountDTO dto);

    /**
     * 删除账号（软删）：账号从列表消失、立刻踢下线，订单等历史数据不受影响。
     */
    void delete(Long id);

    void resetPassword(Long id, String password);
}
