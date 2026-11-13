#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 docs/legal/*.html 生成文章灌库脚本（HTML 里的引号需要转义，手写易错）。
用法：python tools/gen_legal_seed.py（在仓库根目录执行）

产出两个文件：
  docs/sql/seed-legal-articles.sql    —— 首次灌入（幂等，NOT EXISTS 判断）
  docs/sql/update-legal-articles.sql  —— 按标题更新正文（已插过旧内容时刷新用）
"""
import io

BACKSLASH = chr(92)
QUOTE = chr(39)
NL = chr(10)
TAB = chr(9)


def esc(s):
    return s.replace(BACKSLASH, BACKSLASH * 2).replace(QUOTE, QUOTE * 2)


def read(p):
    return io.open(p, encoding='utf-8').read().strip()


ua = read('docs/legal/user-agreement.html')
pp = read('docs/legal/privacy-policy.html')


def article_sql(title, intro, content, sort):
    return (
        "-- 《" + title + "》" + NL
        + "INSERT INTO `promotion_article`" + NL
        + "    (`category_id`, `title`, `author`, `cover`, `content`, `introduction`, `sort_order`, `status`," + NL
        + "     `recommend_hot`, `recommend_banner`, `browse_count`, `spu_id`, `is_deleted`, `create_time`)" + NL
        + "SELECT c.`id`, '" + title + "', '杰米多多', '', '" + esc(content) + "', '" + intro + "', "
        + str(sort) + ", 1, 0, 0, 0, 0, 0, NOW()" + NL
        + "FROM `promotion_article_category` c" + NL
        + "WHERE c.`name` = '协议与公告' AND c.`is_deleted` = 0" + NL
        + "  AND NOT EXISTS (SELECT 1 FROM `promotion_article` WHERE `title` = '" + title + "' AND `is_deleted` = 0);" + NL
    )


header = """-- ============================================================================
-- 小程序「用户协议」「隐私政策」初始化文章（2026-11-04）
--
-- 用途：小程序「我的 - 关于我们」里的「用户协议」「隐私政策」两个入口，
--       走 /pages/article/detail?title=用户协议 与 ?title=隐私政策，
--       后端按标题精确匹配取文（ArticleMapper.findEnabledByTitle：title 精确匹配 + status=1 + is_deleted=0），
--       所以这两篇文章的 title 必须严格等于「用户协议」「隐私政策」。
--
-- 正文来源：docs/legal/user-agreement.html 与 docs/legal/privacy-policy.html（富文本 HTML，
--          后台文章编辑器可直接编辑维护，本脚本只负责初始灌入）。
--
-- 执行范围：本地库 + 生产库执行同一份 SQL（改本地必须同步生产）。
--
-- 注意：Windows 下用 mysql 客户端导入必须加 --default-character-set=utf8mb4，否则中文乱码：
--   mysql -h127.0.0.1 -ue_commerce_db -p"$DB_PASSWORD" --default-character-set=utf8mb4 e_commerce_db < docs/sql/seed-legal-articles.sql
--   （口令从环境变量 DB_PASSWORD 取，不要写进仓库）
-- ============================================================================

-- ---------- 1. 文章分类 ----------
INSERT INTO `promotion_article_category` (`name`, `pic_url`, `sort_order`, `status`, `is_deleted`, `create_time`)
SELECT '协议与公告', '', 1, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `promotion_article_category` WHERE `name` = '协议与公告' AND `is_deleted` = 0);

-- ---------- 2. 用户协议 ----------
"""

sql = (header
       + article_sql('用户协议', '使用本平台服务前请认真阅读的用户服务协议全文。', ua, 1)
       + "-- ---------- 3. 隐私政策 ----------" + NL
       + article_sql('隐私政策', '我们如何收集、使用、存储与保护你的个人信息，以及你如何管理自己的信息。', pp, 2))

io.open('docs/sql/seed-legal-articles.sql', 'w', encoding='utf-8').write(sql)
print('生成 docs/sql/seed-legal-articles.sql，', len(sql), '字符')

# ---------- 按标题更新正文（已插过旧内容时刷新用） ----------
update_header = """-- ============================================================================
-- 刷新「用户协议」「隐私政策」的正文（按标题匹配，只更新 content 与 update_time）
-- 正文改版后执行：本地库 + 生产库执行同一份 SQL
--   mysql -h127.0.0.1 -ue_commerce_db -p"$DB_PASSWORD" --default-character-set=utf8mb4 e_commerce_db < docs/sql/update-legal-articles.sql
--   （口令从环境变量 DB_PASSWORD 取，不要写进仓库）
-- ============================================================================

"""
update_sql = update_header
for title, content in (('用户协议', ua), ('隐私政策', pp)):
    update_sql += (
        "UPDATE `promotion_article`" + NL
        + "SET `content` = '" + esc(content) + "'," + NL
        + "    `update_time` = NOW()" + NL
        + "WHERE `title` = '" + title + "' AND `is_deleted` = 0;" + NL + NL
    )

io.open('docs/sql/update-legal-articles.sql', 'w', encoding='utf-8').write(update_sql)
print('生成 docs/sql/update-legal-articles.sql，', len(update_sql), '字符')
