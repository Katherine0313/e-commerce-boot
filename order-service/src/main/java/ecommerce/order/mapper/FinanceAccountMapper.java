package ecommerce.order.mapper;

import ecommerce.order.entity.FinanceAccount;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款账户 Mapper（finance_account 表）。
 */
public interface FinanceAccountMapper {

    int insert(FinanceAccount account);

    /** 编辑名称 / 商户号 / 备注（动态 set，未传字段保持原值） */
    int update(FinanceAccount account);

    /** 查未删除的账户 */
    FinanceAccount findById(@Param("id") Long id);

    /** 按渠道 + 商户号查（含已删除，唯一索引是全表生效的） */
    FinanceAccount findByChannelAndMerchant(@Param("channel") String channel,
                                            @Param("merchantNo") String merchantNo);

    List<FinanceAccount> listAdmin(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                   @Param("channel") String channel, @Param("status") Integer status,
                                   @Param("keyword") String keyword);

    long countAdmin(@Param("channel") String channel, @Param("status") Integer status,
                    @Param("keyword") String keyword);

    /** 启用中的账户（下拉用），默认账户排前面 */
    List<FinanceAccount> listEnabled();

    /** 清除同渠道的默认标记（excludeId 非空时跳过自己） */
    int clearDefault(@Param("channel") String channel, @Param("excludeId") Long excludeId,
                     @Param("now") LocalDateTime now);

    int setDefault(@Param("id") Long id, @Param("now") LocalDateTime now);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("now") LocalDateTime now);

    /** 软删除，同时清掉默认标记 */
    int delete(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 恢复已软删除的同渠道同商户号账户，避免唯一索引把旧商户号永久占死 */
    int restore(@Param("id") Long id, @Param("accountName") String accountName,
                @Param("isDefault") Integer isDefault, @Param("status") Integer status,
                @Param("remark") String remark, @Param("now") LocalDateTime now);

    /** 按渠道取默认启用账户 ID，取不到返回 null（收款单 account_id 留空） */
    Long findDefaultIdByChannel(@Param("channel") String channel);
}
