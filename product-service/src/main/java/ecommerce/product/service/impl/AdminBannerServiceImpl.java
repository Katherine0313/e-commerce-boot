package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.product.constants.BannerConstants;
import ecommerce.product.dto.AdminBannerDTO;
import ecommerce.product.entity.Banner;
import ecommerce.product.mapper.BannerMapper;
import ecommerce.product.service.AdminBannerService;
import ecommerce.product.support.BannerImagesCodec;
import ecommerce.product.vo.BannerVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台 Banner 管理实现。
 */
@Service
public class AdminBannerServiceImpl implements AdminBannerService {

    private final BannerMapper bannerMapper;
    private final BannerImagesCodec bannerImagesCodec;

    public AdminBannerServiceImpl(BannerMapper bannerMapper, BannerImagesCodec bannerImagesCodec) {
        this.bannerMapper = bannerMapper;
        this.bannerImagesCodec = bannerImagesCodec;
    }

    @Override
    public PageVO<BannerVO> list(int page, int pageSize, String keyword, Integer position, Integer status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        int offset = (safePage - 1) * safePageSize;

        List<BannerVO> list = bannerMapper.listAdmin(safeKeyword, position, status, offset, safePageSize)
                .stream().map(this::toVO).toList();
        long total = bannerMapper.countAdmin(safeKeyword, position, status);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public BannerVO get(Long id) {
        return toVO(requireBanner(id));
    }

    @Override
    @Transactional
    public Long create(AdminBannerDTO dto) {
        validate(dto, null);
        String imagesJson = bannerImagesCodec.encode(dto.getImages());
        List<String> images = bannerImagesCodec.decode(imagesJson, null);
        Banner banner = Banner.builder()
                .title(dto.getTitle().trim())
                .image(images.get(0))
                .images(imagesJson)
                .position(dto.getPosition())
                .linkType(dto.getLinkType())
                .linkValue(dto.getLinkValue() == null ? "" : dto.getLinkValue().trim())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .isDeleted(0)
                .createTime(LocalDateTime.now())
                .build();
        bannerMapper.insert(banner);
        return banner.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminBannerDTO dto) {
        Banner banner = requireBanner(id);
        validate(dto, id);
        String imagesJson = bannerImagesCodec.encode(dto.getImages());
        List<String> images = bannerImagesCodec.decode(imagesJson, null);
        banner.setId(id);
        banner.setTitle(dto.getTitle().trim());
        banner.setImage(images.get(0));
        banner.setImages(imagesJson);
        banner.setPosition(dto.getPosition());
        banner.setLinkType(dto.getLinkType());
        banner.setLinkValue(dto.getLinkValue() == null ? "" : dto.getLinkValue().trim());
        banner.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        banner.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        banner.setUpdateTime(LocalDateTime.now());
        bannerMapper.update(banner);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        requireBanner(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（禁用）或 1（启用）");
        }
        bannerMapper.updateStatus(id, status, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireBanner(id);
        bannerMapper.softDelete(id, LocalDateTime.now());
    }

    private Banner requireBanner(Long id) {
        Banner banner = bannerMapper.findById(id);
        if (banner == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Banner 不存在");
        }
        return banner;
    }

    /**
     * 表单校验：
     * 标题、图片必填；位置只能是约定的三种；跳转类型与跳转目标要配套；
     * 外部链接只允许 http/https。
     */
    private void validate(AdminBannerDTO dto, Long excludeId) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请输入标题");
        }
        if (dto.getTitle().trim().length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "标题不能超过 64 个字符");
        }
        Integer position = dto.getPosition();
        if (position == null
                || (position != BannerConstants.POSITION_MINI_HOME
                && position != BannerConstants.POSITION_MINI_CATEGORY
                && position != BannerConstants.POSITION_PC_HOME)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择正确的广告位置");
        }
        Integer linkType = dto.getLinkType() == null ? BannerConstants.LINK_NONE : dto.getLinkType();
        if (linkType < BannerConstants.LINK_NONE || linkType > BannerConstants.LINK_URL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "跳转类型不正确");
        }
        String linkValue = dto.getLinkValue() == null ? "" : dto.getLinkValue().trim();
        if (linkType != BannerConstants.LINK_NONE) {
            if (linkValue.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写跳转目标");
            }
            if (linkType == BannerConstants.LINK_URL
                    && !linkValue.startsWith("http://") && !linkValue.startsWith("https://")) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "外部链接必须以 http:// 或 https:// 开头");
            }
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "状态只能是 0（禁用）或 1（启用）");
        }
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
                .createTime(b.getCreateTime())
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
