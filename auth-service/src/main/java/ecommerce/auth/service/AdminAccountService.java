package ecommerce.auth.service;

import ecommerce.auth.dto.AdminAccountDTO;
import ecommerce.auth.vo.AdminAccountVO;
import ecommerce.common.PageVO;

/**
 * 后台管理员账号管理。
 */
public interface AdminAccountService {

    PageVO<AdminAccountVO> list(int page, int pageSize, String keyword, Integer status);

    AdminAccountVO get(Long id);

    Long create(AdminAccountDTO dto);

    void update(Long id, AdminAccountDTO dto);

    void resetPassword(Long id, String password);
}
