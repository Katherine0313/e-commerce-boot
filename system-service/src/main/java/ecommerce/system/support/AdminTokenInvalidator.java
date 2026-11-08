package ecommerce.system.support;

import ecommerce.security.AdminTokenRevocationStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * 管理员令牌失效辅助：在事务提交后写入失效标记。
 * <p>
 * 放在 afterCommit 是为了避免事务回滚时把用户误踢下线。
 */
@Component
public class AdminTokenInvalidator {

    private final AdminTokenRevocationStore revocationStore;

    public AdminTokenInvalidator(AdminTokenRevocationStore revocationStore) {
        this.revocationStore = revocationStore;
    }

    /** 单个管理员立即失效 */
    public void invalidateAfterCommit(Long adminId) {
        if (adminId == null) {
            return;
        }
        invalidateAfterCommit(List.of(adminId));
    }

    /** 批量失效；集合先复制，避免延迟加载/可变集合在提交后变化 */
    public void invalidateAfterCommit(Collection<Long> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return;
        }
        List<Long> ids = adminIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    revocationStore.invalidateAll(ids);
                }
            });
        } else {
            revocationStore.invalidateAll(ids);
        }
    }
}
