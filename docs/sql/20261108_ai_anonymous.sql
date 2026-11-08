-- ============================================================
-- AI 聊天放开匿名访问：新增 anonymous_id 列，用于跟踪未登录访客的会话
-- 登录用户：user_id 非空，anonymous_id 为空
-- 匿名用户：user_id 为空，anonymous_id 为前端生成的 UUID
-- ============================================================

ALTER TABLE `ai_chat_conversation`
  ADD COLUMN `anonymous_id` varchar(64) DEFAULT NULL COMMENT '匿名访客标识（未登录用户）' AFTER `user_id`,
  ADD KEY `idx_conv_anon` (`anonymous_id`, `source`);

ALTER TABLE `ai_chat_message`
  ADD COLUMN `anonymous_id` varchar(64) DEFAULT NULL COMMENT '匿名访客标识' AFTER `user_id`;
