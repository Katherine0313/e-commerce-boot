SELECT (SELECT COUNT(*) FROM ai_tool) tools,
       (SELECT COUNT(*) FROM ai_chat_role) roles,
       (SELECT COUNT(*) FROM ai_chat_role_tool) binds,
       (SELECT COUNT(*) FROM admin_menu WHERE id BETWEEN 1000 AND 1017) menus,
       (SELECT COUNT(*) FROM admin_role_menu WHERE menu_id BETWEEN 1000 AND 1017) grants,
       (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_kb_qa' AND COLUMN_NAME='embedding') emb;
