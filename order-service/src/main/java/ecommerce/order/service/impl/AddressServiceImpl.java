package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AddAddressDTO;
import ecommerce.order.dto.UpdateAddressDTO;
import ecommerce.order.entity.Address;
import ecommerce.order.mapper.AddressMapper;
import ecommerce.order.service.AddressService;
import ecommerce.order.vo.AddressVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址 Service 实现。
 *
 * 默认地址唯一性由「clearDefault 先清 0 + 再落新默认」保证，并加事务兜底；
 * 删除默认地址后自动把最新一条（id 最大）设为默认。
 */
@Slf4j
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
        return addressMapper.listByUser(currentUserId(), currentShopId()).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public AddressVO add(AddAddressDTO dto) {
        validate(dto.getConsignee(), dto.getPhone(), dto.getRegion(), dto.getDetailAddress());
        Long userId = currentUserId();
        String consignee = dto.getConsignee().trim();
        String phone = dto.getPhone().trim();
        String region = dto.getRegion().trim();
        String detailAddress = dto.getDetailAddress().trim();

        List<Address> addresses = addressMapper.listByUser(userId, currentShopId());

        // 幂等：同用户完全相同的地址复用已有那条，不再新增、不改默认标记
        Address duplicate = findDuplicate(addresses, null, consignee, phone, region, detailAddress);
        if (duplicate != null) {
            log.info("地址新增幂等命中，复用已有地址，userId:{}, addressId:{}", userId, duplicate.getId());
            return toVO(duplicate);
        }

        // 第一条地址自动设为默认；或显式传 is_default=1 也设默认
        boolean hasAddress = !addresses.isEmpty();
        boolean shouldDefault = (dto.getIsDefault() != null && dto.getIsDefault() == 1) || !hasAddress;
        if (shouldDefault) {
            log.info("清空用户原有默认地址，userId:{}", userId);
            addressMapper.clearDefault(userId, currentShopId());
        }

        Address address = Address.builder()
                .shopId(currentShopId())
                .userId(userId)
                .consignee(consignee)
                .phone(phone)
                .region(region)
                .detailAddress(detailAddress)
                .isDefault(shouldDefault ? 1 : 0)
                .build();
        int rows = addressMapper.insert(address);
        log.info("成功插入:{}行", rows);
        return toVO(address);
    }

    @Override
    @Transactional
    public void update(Long id, UpdateAddressDTO dto) {
        validate(dto.getConsignee(), dto.getPhone(), dto.getRegion(), dto.getDetailAddress());
        Long userId = currentUserId();
        Address existing = addressMapper.findById(id, userId, currentShopId());
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "地址不存在");
        }

        String consignee = dto.getConsignee().trim();
        String phone = dto.getPhone().trim();
        String region = dto.getRegion().trim();
        String detailAddress = dto.getDetailAddress().trim();

        // 排除自身后查重，防止改成与另一条完全相同的地址
        if (findDuplicate(addressMapper.listByUser(userId, currentShopId()), id, consignee, phone, region, detailAddress) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "该地址已存在");
        }

        // 改成默认：先清掉其他默认；否则保持原默认标志不变（不允许取消默认）
        boolean shouldDefault = dto.getIsDefault() != null && dto.getIsDefault() == 1;
        if (shouldDefault) {
            addressMapper.clearDefault(userId, currentShopId());
        }

        existing.setConsignee(consignee);
        existing.setPhone(phone);
        existing.setRegion(region);
        existing.setDetailAddress(detailAddress);
        existing.setIsDefault(shouldDefault ? 1 : existing.getIsDefault());
        addressMapper.update(existing);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        Long userId = currentUserId();
        Address existing = addressMapper.findById(id, userId, currentShopId());
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "地址不存在");
        }
        addressMapper.deleteById(id, userId, currentShopId());
        // 删的是默认地址：自动把最新一条设为默认（无剩余地址时该语句 no-op）
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            addressMapper.setDefaultToLatest(userId, currentShopId());
        }
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    // 当前店铺：C 端 user 令牌带 shopId（多租户隔离键），无 shopId 时回退模板店
    private Long currentShopId() {
        Long shopId = UserContext.getShopId();
        return shopId == null ? TEMPLATE_SHOP_ID : shopId;
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

    /** 在同店铺地址列表中找与四字段完全一致的地址（excludeId 用于编辑时排除自身），找不到返回 null */
    private Address findDuplicate(List<Address> addresses, Long excludeId, String consignee, String phone, String region, String detailAddress) {
        return addresses.stream()
                .filter(a -> excludeId == null || !excludeId.equals(a.getId()))
                .filter(a -> consignee.equals(a.getConsignee())
                        && phone.equals(a.getPhone())
                        && region.equals(a.getRegion())
                        && detailAddress.equals(a.getDetailAddress()))
                .findFirst()
                .orElse(null);
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