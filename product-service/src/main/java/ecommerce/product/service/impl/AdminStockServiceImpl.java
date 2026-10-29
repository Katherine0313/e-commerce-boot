package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminStockDTO;
import ecommerce.product.entity.Stock;
import ecommerce.product.mapper.StockMapper;
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

    private final StockMapper stockMapper;

    public AdminStockServiceImpl(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @Override
    public List<StockVO> list(String search) {
        return stockMapper.listWithProducts(search);
    }

    @Override
    public StockStatusVO statusConstants() {
        return StockStatusVO.builder()
                .map(Map.of("high", "高库存", "normal", "正常", "low", "低库存"))
                .color(Map.of("high", "blue", "normal", "green", "low", "red"))
                .build();
    }

    @Override
    public void create(AdminStockDTO dto) {
        validate(dto);
        Stock existing = stockMapper.findByProductId(dto.getProductId());
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            // product_id 重复 → 更新（动态 set，null 字段保留原值）
            Stock update = new Stock();
            update.setId(existing.getId());
            update.setAlertEnabled(dto.getAlertEnabled());
            update.setMinStock(dto.getMinStock());
            update.setMaxStock(dto.getMaxStock());
            update.setUpdateTime(now);
            stockMapper.update(update);
        } else {
            Stock s = new Stock();
            s.setProductId(dto.getProductId());
            s.setAlertEnabled(dto.getAlertEnabled());
            s.setMinStock(dto.getMinStock());
            s.setMaxStock(dto.getMaxStock());
            s.setCreateTime(now);
            s.setUpdateTime(now);
            stockMapper.insert(s);
        }
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
