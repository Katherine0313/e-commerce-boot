package ecommerce.product.service.impl;

import ecommerce.product.constants.BannerConstants;
import ecommerce.product.entity.Banner;
import ecommerce.product.mapper.BannerMapper;
import ecommerce.product.service.BannerService;
import ecommerce.product.support.BannerImagesCodec;
import ecommerce.product.vo.BannerVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Banner C 端服务实现（小程序 / PC 商城轮播，免登录）。
 */
@Service
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;
    private final BannerImagesCodec bannerImagesCodec;

    public BannerServiceImpl(BannerMapper bannerMapper, BannerImagesCodec bannerImagesCodec) {
        this.bannerMapper = bannerMapper;
        this.bannerImagesCodec = bannerImagesCodec;
    }

    @Override
    public List<BannerVO> listEnabled(Integer position) {
        return bannerMapper.listEnabled(position).stream().map(this::toVO).toList();
    }

    @Override
    public void browse(Long id) {
        // 记录不存在（已删除）时更新影响 0 行，直接忽略，不给前端报错
        bannerMapper.increaseBrowseCount(id);
    }

    private BannerVO toVO(Banner b) {
        return BannerVO.builder()
                .id(b.getId())
                .title(b.getTitle())
                .image(b.getImage())
                .images(bannerImagesCodec.decode(b.getImages(), b.getImage()))
                .position(b.getPosition())
                .positionDesc(positionDesc(b.getPosition()))
                .linkType(b.getLinkType())
                .linkValue(b.getLinkValue())
                .sortOrder(b.getSortOrder())
                .status(b.getStatus())
                .statusDesc(b.getStatus() != null && b.getStatus() == 1 ? "启用" : "禁用")
                .browseCount(b.getBrowseCount())
                .build();
    }

    private String positionDesc(Integer position) {
        if (position == null) {
            return "";
        }
        return switch (position) {
            case BannerConstants.POSITION_MINI_HOME -> "小程序-首页";
            case BannerConstants.POSITION_MINI_CATEGORY -> "小程序-分类页";
            case BannerConstants.POSITION_PC_HOME -> "PC商城-首页";
            default -> "";
        };
    }
}
