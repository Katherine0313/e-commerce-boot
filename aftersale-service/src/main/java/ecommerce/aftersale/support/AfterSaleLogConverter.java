package ecommerce.aftersale.support;

import ecommerce.aftersale.entity.AfterSaleLog;
import ecommerce.aftersale.enums.AfterSaleOperateTypeEnum;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.vo.AfterSaleLogVO;

import java.util.List;

/**
 * 售后变更流水 → VO 的转换。
 * <p>
 * C 端与后台共用同一个 VO，但**买家侧不返回 merchantRemark**（商家内部备注），
 * 所以转换时带一个开关。
 */
public final class AfterSaleLogConverter {

    private AfterSaleLogConverter() {
    }

    /**
     * 转 VO。
     *
     * @param logs              流水列表
     * @param withMerchantRemark 是否带上商家内部备注（后台 true，买家 false）
     */
    public static List<AfterSaleLogVO> toVOList(List<AfterSaleLog> logs, boolean withMerchantRemark) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }
        return logs.stream().map(log -> toVO(log, withMerchantRemark)).toList();
    }

    /** 单条转 VO */
    public static AfterSaleLogVO toVO(AfterSaleLog log, boolean withMerchantRemark) {
        AfterSaleStatusEnum before = AfterSaleStatusEnum.fromCode(log.getBeforeStatus());
        AfterSaleStatusEnum after = AfterSaleStatusEnum.fromCode(log.getAfterStatus());
        AfterSaleOperateTypeEnum operateType = AfterSaleOperateTypeEnum.getByCode(log.getOperateType());
        return AfterSaleLogVO.builder()
                .id(log.getId())
                .afterSaleId(log.getAfterSaleId())
                .userType(log.getUserType())
                .userTypeDesc(userTypeDesc(log.getUserType()))
                .beforeStatus(log.getBeforeStatus())
                .beforeStatusDesc(before == null ? null : before.getDesc())
                .afterStatus(log.getAfterStatus())
                .afterStatusDesc(after == null ? null : after.getDesc())
                .operateType(log.getOperateType())
                .operateTypeDesc(operateType == null ? null : operateType.getDesc())
                .content(log.getContent())
                .merchantRemark(withMerchantRemark ? log.getMerchantRemark() : null)
                .createdAt(log.getCreatedAt())
                .build();
    }

    /** 操作人类型中文 */
    private static String userTypeDesc(Integer userType) {
        if (userType == null) {
            return null;
        }
        return AfterSaleLog.USER_TYPE_ADMIN == userType ? "商家" : "买家";
    }
}
