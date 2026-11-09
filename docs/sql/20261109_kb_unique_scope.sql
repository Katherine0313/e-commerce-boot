-- 2026-11-09 知识库唯一键扩展（生产同步）
-- 背景：ai_kb_qa 唯一键 (category, question) 不含 scope，客服/运营两库同名问答会互相覆盖
-- 内容：唯一键改为 (scope, category, question)，配合知识库 Excel 批量导入按 scope 判重 upsert
-- 兼容：旧索引名可能是 uk_kb_category_question（表由 kb_qa 重命名而来）或 uk_ai_kb_category_question
-- 幂等：存储过程按 information_schema 判断索引存在性，可安全重复执行
-- 前置检查：SELECT scope, category, question, COUNT(*) c FROM ai_kb_qa
--           GROUP BY scope, category, question HAVING c > 1; 有重复行需先人工去重

DROP PROCEDURE IF EXISTS _kb_uk_scope_migration;
DELIMITER $$
CREATE PROCEDURE _kb_uk_scope_migration()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'ai_kb_qa'
               AND index_name = 'uk_kb_category_question') THEN
    ALTER TABLE ai_kb_qa DROP INDEX uk_kb_category_question;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'ai_kb_qa'
               AND index_name = 'uk_ai_kb_category_question') THEN
    ALTER TABLE ai_kb_qa DROP INDEX uk_ai_kb_category_question;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'ai_kb_qa'
                   AND index_name = 'uk_ai_kb_scope_category_question') THEN
    ALTER TABLE ai_kb_qa ADD UNIQUE KEY uk_ai_kb_scope_category_question (scope, category, question);
  END IF;
END$$
DELIMITER ;
CALL _kb_uk_scope_migration();
DROP PROCEDURE _kb_uk_scope_migration;

-- 验证：应只剩 PRIMARY / uk_ai_kb_scope_category_question / idx_kb_scope / idx_kb_status
SHOW INDEX FROM ai_kb_qa;
