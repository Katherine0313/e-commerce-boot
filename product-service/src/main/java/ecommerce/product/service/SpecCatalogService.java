package ecommerce.product.service;

import ecommerce.product.dto.SpecGroupDTO;
import ecommerce.product.dto.SpecValueDTO;
import ecommerce.product.vo.SpecGroupVO;
import ecommerce.product.vo.SpecValueVO;

import java.util.List;

/**
 * 销售规格字典（规格库）管理。
 */
public interface SpecCatalogService {
    List<SpecGroupVO> list();

    /** 按分类过滤：categoryId 可以是任意层级的 id，服务端自动向上找所有祖先；传 null 表示返回全部 */
    List<SpecGroupVO> list(Long categoryId);

    SpecGroupVO createGroup(SpecGroupDTO dto);

    SpecGroupVO updateGroup(Long id, SpecGroupDTO dto);

    void deleteGroup(Long id);

    SpecValueVO addValue(SpecValueDTO dto);

    void deleteValue(Long id);
}
