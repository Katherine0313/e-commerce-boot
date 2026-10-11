package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AddAddressDTO;
import ecommerce.order.dto.UpdateAddressDTO;
import ecommerce.order.entity.Address;
import ecommerce.order.mapper.AddressMapper;
import ecommerce.order.service.AddressService;
import ecommerce.order.vo.AddressVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址 Service 实现。
 * <p>
 * 默认地址唯一性由「clearDefault 先清 0 + 再落新默认」保证，并加事务兜底；
 * 删除默认地址后自动把最新一条（id 最大）设为默认。
 */
@Service
public class AddressServiceImpl implements AddressService {

    /** 模板店（杰米小店）：多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;

    private final AddressMapper addressMapper;

    public AddressServiceImpl(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<AddressVO> list() {
        return addressMapper.listByUser(currentUserId(), TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public void add(AddAddressDTO dto) {
        validate(dto.getConsignee(), dto.getPhone(), dto.getRegion(), dto.getDetailAddress());
        Long userId = currentUserId();

        // 第一条地址自动设为默认；或显式传 is_default=1 也设默认
        boolean hasAddress = !addressMapper.listByUser(userId, TEMPLATE_SHOP_ID).isEmpty();
        boolean shouldDefault = (dto.getIsDefault() != null && dto.getIsDefault() == 1) || !hasAddress;
        if (shouldDefault) {
            addressMapper.clearDefault(userId, TEMPLATE_SHOP_ID);
        }

        Address address = Address.builder()
                .shopId(TEMPLATE_SHOP_ID)
                .userId(userId)
                .consignee(dto.getConsignee().trim())
                .phone(dto.getPhone().trim())
                .region(dto.getRegion().trim())
                .detailAddress(dto.getDetailAddress().trim())
                .isDefault(shouldDefault ? 1 : 0)
                .build();
        addressMapper.insert(address);
    }

    @Override
    @Transactional
    public void update(Long id, UpdateAddressDTO dto) {
        validate(dto.getConsignee(), dto.getPhone(), dto.getRegion(), dto.getDetailAddress());
        Long userId = currentUserId();
        Address existing = addressMapper.findById(id, TEMPLATE_SHOP_ID);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "地址不存在");
        }

        // 改成默认：先清掉其他默认；否则保持原默认标志不变（不允许取消默认）
        boolean shouldDefault = dto.getIsDefault() != null && dto.getIsDefault() == 1;
        if (shouldDefault) {
            addressMapper.clearDefault(userId, TEMPLATE_SHOP_ID);
        }

        existing.setConsignee(dto.getConsignee().trim());
        existing.setPhone(dto.getPhone().trim());
        existing.setRegion(dto.getRegion().trim());
        existing.setDetailAddress(dto.getDetailAddress().trim());
        existing.setIsDefault(shouldDefault ? 1 : existing.getIsDefault());
        addressMapper.update(existing);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        Long userId = currentUserId();
        Address existing = addressMapper.findById(id, TEMPLATE_SHOP_ID);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        addressMapper.deleteById(id, TEMPLATE_SHOP_ID);
        // 删的是默认地址：自动把最新一条设为默认（无剩余地址时该语句 no-op）
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            addressMapper.setDefaultToLatest(userId, TEMPLATE_SHOP_ID);
        }
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    /** 四个必填字段非空校验 + 手机号 11 位数字格式 */
    private void validate(String consignee, String phone, String region, String detailAddress) {
        if (consignee == null || consignee.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收货人不能为空");
        }
        if (phone == null || !phone.trim().matches("\\d{11}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        if (region == null || region.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "省市区不能为空");
        }
        if (detailAddress == null || detailAddress.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "详细地址不能为空");
        }
    }

    private AddressVO toVO(Address a) {
        return AddressVO.builder()
                .id(a.getId())
                .consignee(a.getConsignee())
                .phone(a.getPhone())
                .region(a.getRegion())
                .detailAddress(a.getDetailAddress())
                .isDefault(a.getIsDefault())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
