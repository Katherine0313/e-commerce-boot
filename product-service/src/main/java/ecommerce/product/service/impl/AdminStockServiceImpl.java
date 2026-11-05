package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.LogDiff;
import ecommerce.log.OperationLog;
import ecommerce.product.dto.AdminStockDTO;
import ecommerce.product.entity.StockAlert;
import ecommerce.product.mapper.StockAlertMapper;
import ecommerce.product.service.AdminStockService;
import ecommerce.product.vo.StockStatusVO;
import ecommerce.product.vo.StockVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台库存预警管理 Service 实现。
 * <p>
 * 库存状态（high/normal/low）由前端拿 products.stock 与 min/max 实时算（见前端 utils/stock.js），
 * 后端只存「预警配置」，不落库存状态。
 */
@Service
public class AdminStockServiceImpl implements AdminStockService {

    private final StockAlertMapper stockAlertMapper;

    public AdminStockServiceImpl(StockAlertMapper stockAlertMapper) {
        this.stockAlertMapper = stockAlertMapper;
    }

    @Override
    public List<StockVO> list(String search) {
        return stockAlertMapper.listWithProducts(search);
    }

    @Override
    public StockStatusVO statusConstants() {
        return StockStatusVO.builder()
                .map(Map.of("high", "高库存", "normal", "正常", "low", "低库存"))
                .color(Map.of("high", "blue", "normal", "green", "low", "red"))
                .build();
    }

    @Override
    @OperationLog(module = "product", action = "STOCK_ALERT", bizId = "#dto.productId")
    public void create(AdminStockDTO dto) {
        validate(dto);
        StockAlert existing = stockAlertMapper.findByProductId(dto.getProductId());
        // 关闭预警 = 删除配置：stock_alert 的 id 就是列表里的编号，留着会让未开启预警的商品也占用编号
        if (!Integer.valueOf(1).equals(dto.getAlertEnabled())) {
            if (existing != null) {
                stockAlertMapper.deleteByProductId(dto.getProductId());
                // 关闭预警等于把配置删掉，日志里记开关的变化
                LogDetail.add(LogDiff.of("预警开关", existing.getAlertEnabled(), 0,
                        AdminStockServiceImpl::alertEnabledText));
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            // product_id 重复 → 更新（动态 set，null 字段保留原值）
            // 变更内容：开关 + 上下限阈值，值没变就不记
            LogDetail.add(LogDiff.of("预警开关", existing.getAlertEnabled(), dto.getAlertEnabled(),
                    AdminStockServiceImpl::alertEnabledText));
            LogDetail.add(LogDiff.of("最小库存阈值", existing.getMinStock(), dto.getMinStock()));
            LogDetail.add(LogDiff.of("最大库存阈值", existing.getMaxStock(), dto.getMaxStock()));
            StockAlert update = new StockAlert();
            update.setId(existing.getId());
            update.setAlertEnabled(dto.getAlertEnabled());
            update.setMinStock(dto.getMinStock());
            update.setMaxStock(dto.getMaxStock());
            update.setUpdateTime(now);
            stockAlertMapper.update(update);
        } else {
            // 新增没有「改前值」，直接记下这次开的配置
            LogDetail.add("新增库存预警：预警开关【已开启】、最小库存阈值【" + dto.getMinStock()
                    + "】、最大库存阈值【" + dto.getMaxStock() + "】");
            StockAlert s = new StockAlert();
            s.setProductId(dto.getProductId());
            s.setAlertEnabled(dto.getAlertEnabled());
            s.setMinStock(dto.getMinStock());
            s.setMaxStock(dto.getMaxStock());
            s.setCreateTime(now);
            s.setUpdateTime(now);
            stockAlertMapper.insert(s);
        }
    }

    /** 预警开关翻译：1 已开启 / 0 已关闭 */
    private static String alertEnabledText(Object value) {
        if (value instanceof Integer enabled) {
            return enabled == 1 ? "已开启" : "已关闭";
        }
        return String.valueOf(value);
    }

    // 校验：alertEnabled ∈ {0,1}；min/max 非空时 >= 0；开启预警时 min/max 必填且 min <= max
    private void validate(AdminStockDTO dto) {
        if (dto.getProductId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
        }
        Integer enabled = dto.getAlertEnabled();
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "预警开关只能是 0 或 1");
        }
        if (dto.getMinStock() != null && dto.getMinStock() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存下限不能为负");
        }
        if (dto.getMaxStock() != null && dto.getMaxStock() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存上限不能为负");
        }
        if (Integer.valueOf(1).equals(enabled)) {
            if (dto.getMinStock() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "开启预警时必须填写库存下限");
            }
            if (dto.getMaxStock() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "开启预警时必须填写库存上限");
            }
            if (dto.getMinStock() > dto.getMaxStock()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "库存下限不能大于库存上限");
            }
        }
    }
}
