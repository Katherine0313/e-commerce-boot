package ecommerce.order.service;

import ecommerce.order.dto.AddAddressDTO;
import ecommerce.order.dto.UpdateAddressDTO;
import ecommerce.order.vo.AddressVO;

import java.util.List;

/**
 * 收货地址 Service。
 */
public interface AddressService {

    List<AddressVO> list();

    void add(AddAddressDTO dto);

    void update(Long id, UpdateAddressDTO dto);

    void remove(Long id);
}
