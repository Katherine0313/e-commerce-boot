package ecommerce.product.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 分类树缓存清理：后台改分类/商品后调用，避免 C 端 30 分钟内读到旧树。
 */
@Slf4j
@Component
public class CategoryCacheEvictor {

    private static final String PATTERN = "category:parent:*";

    private final StringRedisTemplate stringRedisTemplate;

    public CategoryCacheEvictor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 删除所有分类树缓存；Redis 故障只记日志，不影响后台写操作 */
    public void evictAll() {
        try {
            Set<String> keys = stringRedisTemplate.keys(PATTERN);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (RuntimeException e) {
            log.warn("分类缓存清理失败，忽略: {}", e.getMessage());
        }
    }
}
