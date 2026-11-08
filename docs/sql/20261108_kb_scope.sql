-- ============================================================
-- 知识库按角色分库：新增 scope 字段
-- scope='客服' → 商城客服机器人使用
-- scope='运营' → 后台运营助手使用
-- 现有数据默认为 '客服'
-- ============================================================

ALTER TABLE `ai_kb_qa`
  ADD COLUMN `scope` varchar(16) NOT NULL DEFAULT '客服' COMMENT '知识适用范围：客服/运营' AFTER `category`,
  ADD KEY `idx_kb_scope` (`scope`);
