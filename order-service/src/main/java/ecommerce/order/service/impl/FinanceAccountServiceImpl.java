package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.OperationLog;
import ecommerce.order.dto.FinanceAccountDTO;
import ecommerce.order.dto.FinanceAccountQueryDTO;
import ecommerce.order.entity.FinanceAccount;
import ecommerce.order.enums.PayTypeEnum;
import ecommerce.order.mapper.FinanceAccountMapper;
import ecommerce.order.service.FinanceAccountService;
import ecommerce.order.vo.FinanceAccountVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款账户 Service 实现。
 * <p>
 * 一个渠道可以有多个账户，但默认账户互斥；删除为软删除，历史收款单仍能显示账户名。
 */
@Service
public class FinanceAccountServiceImpl implements FinanceAccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FinanceAccountMapper financeAccountMapper;

    public FinanceAccountServiceImpl(FinanceAccountMapper financeAccountMapper) {
        this.financeAccountMapper = financeAccountMapper;
    }

    @Override
    public PageVO<FinanceAccountVO> list(int page, int pageSize, FinanceAccountQueryDTO query) {
        FinanceAccountQueryDTO q = query == null ? new FinanceAccountQueryDTO() : query;
        String channel = blankToNull(q.getChannel());
        if (channel != null) {
            channel = normalizeChannel(channel);
        }
        Integer status = normalizeNullableStatus(q.getStatus());
        String keyword = blankToNull(q.getKeyword());
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        List<FinanceAccountVO> list = financeAccountMapper.listAdmin(offset, safeSize, channel, status, keyword)
                .stream().map(this::toVO).toList();
        long total = financeAccountMapper.countAdmin(channel, status, keyword);
        return new PageVO<>(list, total, safePage, safeSize);
    }

    @Override
    public List<FinanceAccountVO> listEnabled() {
        return financeAccountMapper.listEnabled().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    @OperationLog(module = "finance", action = "ACCOUNT_SAVE")
    public FinanceAccountVO create(FinanceAccountDTO dto) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写账户信息");
        }
        String accountName = requireName(dto.getAccountName());
        String channel = normalizeChannel(dto.getChannel());
        String merchantNo = requireMerchantNo(dto.getMerchantNo());
        int status = dto.getStatus() == null ? 1 : normalizeStatus(dto.getStatus());
        boolean makeDefault = Integer.valueOf(1).equals(dto.getIsDefault());
        LocalDateTime now = LocalDateTime.now();

        FinanceAccount exist = financeAccountMapper.findByChannelAndMerchant(channel, merchantNo);
        if (exist != null && !Integer.valueOf(1).equals(exist.getIsDeleted())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该渠道下商户号已存在");
        }
        if (makeDefault) {
            financeAccountMapper.clearDefault(channel, null, now);
        }
        // 唯一索引是全表生效的：同渠道同商户号被软删过时直接恢复，避免旧商户号被永久占死
        if (exist != null) {
            financeAccountMapper.restore(exist.getId(), accountName, makeDefault ? 1 : 0, status,
                    dto.getRemark(), now);
            return toVO(financeAccountMapper.findById(exist.getId()));
        }

        FinanceAccount account = FinanceAccount.builder()
                .accountName(accountName)
                .channel(channel)
                .merchantNo(merchantNo)
                .isDefault(makeDefault ? 1 : 0)
                .status(status)
                .remark(dto.getRemark())
                .isDeleted(0)
                .createTime(now)
                .updateTime(now)
                .build();
        financeAccountMapper.insert(account);
        return toVO(account);
    }

    @Override
    @OperationLog(module = "finance", action = "ACCOUNT_SAVE", bizId = "#id")
    public FinanceAccountVO update(Long id, FinanceAccountDTO dto) {
        FinanceAccount exist = requireAccount(id);
        String accountName = requireName(dto == null ? null : dto.getAccountName());
        String merchantNo = blankToNull(dto.getMerchantNo());
        if (merchantNo != null && !merchantNo.equals(exist.getMerchantNo())) {
            FinanceAccount same = financeAccountMapper.findByChannelAndMerchant(exist.getChannel(), merchantNo);
            if (same != null && !same.getId().equals(id)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "该渠道下商户号已存在");
            }
        }
        FinanceAccount update = FinanceAccount.builder()
                .id(id)
                .accountName(accountName)
                .merchantNo(merchantNo)
                .remark(dto.getRemark())
                .updateTime(LocalDateTime.now())
                .build();
        financeAccountMapper.update(update);
        return toVO(financeAccountMapper.findById(id));
    }

    @Override
    @OperationLog(module = "finance", action = "ACCOUNT_STATUS", bizId = "#id")
    public void updateStatus(Long id, Integer status) {
        requireAccount(id);
        if (status == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择状态");
        }
        financeAccountMapper.updateStatus(id, normalizeStatus(status), LocalDateTime.now());
    }

    @Override
    @Transactional
    @OperationLog(module = "finance", action = "ACCOUNT_DEFAULT", bizId = "#id")
    public void setDefault(Long id) {
        FinanceAccount exist = requireAccount(id);
        LocalDateTime now = LocalDateTime.now();
        financeAccountMapper.clearDefault(exist.getChannel(), id, now);
        financeAccountMapper.setDefault(id, now);
    }

    @Override
    @OperationLog(module = "finance", action = "ACCOUNT_REMOVE", bizId = "#id")
    public void delete(Long id) {
        requireAccount(id);
        financeAccountMapper.delete(id, LocalDateTime.now());
    }

    // ==================== 内部工具 ====================

    private FinanceAccount requireAccount(Long id) {
        FinanceAccount account = id == null ? null : financeAccountMapper.findById(id);
        if (account == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "收款账户不存在");
        }
        return account;
    }

    private String requireName(String value) {
        String name = blankToNull(value);
        if (name == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写账户名称");
        }
        return name;
    }

    private String requireMerchantNo(String value) {
        String merchantNo = blankToNull(value);
        if (merchantNo == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写商户号");
        }
        return merchantNo;
    }

    /** 渠道统一收敛成 PayTypeEnum 的 code（大小写/历史数据兼容） */
    private String normalizeChannel(String channel) {
        PayTypeEnum type = PayTypeEnum.fromCode(blankToNull(channel));
        if (type == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收款渠道只能是 WeChat / Alipay / BankCard");
        }
        return type.getCode();
    }

    private Integer normalizeNullableStatus(Integer status) {
        return status == null ? null : normalizeStatus(status);
    }

    private int normalizeStatus(Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 1（启用）或 0（停用）");
        }
        return status;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private FinanceAccountVO toVO(FinanceAccount e) {
        PayTypeEnum type = PayTypeEnum.fromCode(e.getChannel());
        return FinanceAccountVO.builder()
                .id(e.getId())
                .accountName(e.getAccountName())
                .channel(e.getChannel())
                .channelDesc(type == null ? e.getChannel() : type.getDesc())
                .merchantNo(e.getMerchantNo())
                .isDefault(e.getIsDefault())
                .status(e.getStatus())
                .statusDesc(Integer.valueOf(1).equals(e.getStatus()) ? "启用" : "停用")
                .remark(e.getRemark())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }
}
