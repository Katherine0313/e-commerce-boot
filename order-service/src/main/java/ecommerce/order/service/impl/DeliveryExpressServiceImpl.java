package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.OperationLog;
import ecommerce.order.dto.DeliveryExpressDTO;
import ecommerce.order.entity.DeliveryExpress;
import ecommerce.order.mapper.DeliveryExpressMapper;
import ecommerce.order.service.DeliveryExpressService;
import ecommerce.order.vo.DeliveryExpressVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快递公司字典 Service 实现。
 */
@Service
public class DeliveryExpressServiceImpl implements DeliveryExpressService {

    private final DeliveryExpressMapper deliveryExpressMapper;

    public DeliveryExpressServiceImpl(DeliveryExpressMapper deliveryExpressMapper) {
        this.deliveryExpressMapper = deliveryExpressMapper;
    }

    @Override
    public PageVO<DeliveryExpressVO> listAll(int page, int pageSize, String keyword, Integer status) {
        int offset = (page - 1) * pageSize;
        List<DeliveryExpressVO> list = deliveryExpressMapper.listAdmin(offset, pageSize, keyword, status)
                .stream().map(this::toVO).toList();
        long total = deliveryExpressMapper.countAdmin(keyword, status);
        return new PageVO<>(list, total, page, pageSize);
    }

    @Override
    @OperationLog(module = "delivery", action = "EXPRESS_SAVE")
    public DeliveryExpressVO create(DeliveryExpressDTO dto) {
        if (dto == null || dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写快递公司编码");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写快递公司名称");
        }
        String code = dto.getCode().trim();
        if (deliveryExpressMapper.findByCode(code) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该快递公司编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        DeliveryExpress express = DeliveryExpress.builder()
                .code(code)
                .name(dto.getName().trim())
                .logo(dto.getLogo())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .remark(dto.getRemark())
                .isDeleted(0)
                .createTime(now)
                .updateTime(now)
                .build();
        deliveryExpressMapper.insert(express);
        return toVO(express);
    }

    @Override
    @OperationLog(module = "delivery", action = "EXPRESS_SAVE", bizId = "#id")
    public DeliveryExpressVO update(Long id, DeliveryExpressDTO dto) {
        DeliveryExpress exist = deliveryExpressMapper.findById(id);
        if (exist == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "快递公司不存在");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写快递公司名称");
        }
        // 编码建好后不改：orders.courier / 包裹上的 express_code 都按 code 关联
        DeliveryExpress express = DeliveryExpress.builder()
                .id(id)
                .name(dto.getName().trim())
                .logo(dto.getLogo())
                .sortOrder(dto.getSortOrder())
                .status(dto.getStatus())
                .remark(dto.getRemark())
                .updateTime(LocalDateTime.now())
                .build();
        deliveryExpressMapper.update(express);
        return toVO(deliveryExpressMapper.findById(id));
    }

    @Override
    @OperationLog(module = "delivery", action = "EXPRESS_REMOVE", bizId = "#id")
    public void delete(Long id) {
        if (deliveryExpressMapper.findById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "快递公司不存在");
        }
        deliveryExpressMapper.delete(id, LocalDateTime.now());
    }

    @Override
    public List<DeliveryExpressVO> listEnabled() {
        return deliveryExpressMapper.listEnabled().stream().map(this::toVO).toList();
    }

    @Override
    public List<Map<String, String>> courierOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        for (DeliveryExpressVO vo : listEnabled()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", vo.getCode());
            item.put("label", vo.getName());
            options.add(item);
        }
        return options;
    }

    private DeliveryExpressVO toVO(DeliveryExpress e) {
        return DeliveryExpressVO.builder()
                .id(e.getId())
                .code(e.getCode())
                .name(e.getName())
                .logo(e.getLogo())
                .sortOrder(e.getSortOrder())
                .status(e.getStatus())
                .statusDesc(Integer.valueOf(1).equals(e.getStatus()) ? "启用" : "禁用")
                .remark(e.getRemark())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }
}
