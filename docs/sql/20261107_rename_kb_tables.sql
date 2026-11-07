-- 知识库表重命名：kb_qa → ai_kb_qa, kb_miss → ai_kb_miss
-- 执行环境：生产数据库 e_commerce_db
-- 执行前请确认：当前表名仍为 kb_qa / kb_miss（未重命名过）
-- 回滚：RENAME TABLE ai_kb_qa TO kb_qa, ai_kb_miss TO kb_miss;

RENAME TABLE kb_qa TO ai_kb_qa, kb_miss TO ai_kb_miss;

-- 验证
SHOW TABLES LIKE 'ai_kb%';
SELECT COUNT(*) AS qa_count FROM ai_kb_qa;
SELECT COUNT(*) AS miss_count FROM ai_kb_miss;
