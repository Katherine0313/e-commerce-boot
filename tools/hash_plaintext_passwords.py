#!/usr/bin/env python3
"""把库里「明文存储」的历史密码批量升级为 BCrypt（密码本身不变，登录不受影响）。

背景：`auth-service` 的 `PasswordUtil.matches` 兼容两种存储格式 ——
`$2` 开头走 `BCryptPasswordEncoder.matches()`，否则直接明文比对。
`PasswordUtil` 的注释也写了「历史 admin 表存的是明文；首次登录成功后会自动升级为 BCrypt」。

但只要那个账号没被登录过，明文就一直躺在库里 —— 导出/备份时会原样泄露。
本脚本把这些明文密码**就地转成 BCrypt**：同一个密码，同一个账号，登录照常，
但库里和导出文件里不再有明文。

安全约定：
- **全程不打印、不记录明文密码**
- 连接参数从各服务的 `application-dev.yml` 读取，脚本本身不含任何凭据
- 只处理「不是 BCrypt」的值；已是 BCrypt 的直接跳过（幂等）

用法：
    python tools/hash_plaintext_passwords.py            # 处理 admin + user 两张表
    python tools/hash_plaintext_passwords.py --dry-run  # 只报告，不写库
"""
import argparse
import re
import sys
from pathlib import Path

try:
    import bcrypt
    import pymysql
except ImportError as e:  # pragma: no cover
    sys.exit(f"缺少依赖：{e}（pip install pymysql bcrypt）")

ROOT = Path(__file__).resolve().parents[1]
# 处理的表：表名 -> 该表密码所在列
TARGETS = {"admin": "password", "user": "password"}
# 库连接参数从这些文件里找（取第一个能读到的）
CFG_CANDIDATES = [
    ROOT / "order-service" / "src" / "main" / "resources" / "application-dev.yml",
    ROOT / "user-service" / "src" / "main" / "resources" / "application-dev.yml",
]


def read_db_config() -> dict:
    """从 application-dev.yml 读 DB_URL / DB_USERNAME / DB_PASSWORD。"""
    for path in CFG_CANDIDATES:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        cfg = {}
        for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
            m = re.search(rf"^{key}:\s*'?([^'\n]+)'?\s*$", text, re.M)
            if m:
                cfg[key] = m.group(1).strip()
        if "DB_URL" in cfg and "DB_USERNAME" in cfg and "DB_PASSWORD" in cfg:
            m = re.search(r"jdbc:mysql://([^:/]+):(\d+)/([^?]+)", cfg["DB_URL"])
            if m:
                cfg["host"], cfg["port"], cfg["database"] = m.group(1), int(m.group(2)), m.group(3)
                return cfg
    sys.exit("找不到 application-dev.yml，无法读取数据库连接参数")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只报告不写库")
    args = ap.parse_args()

    cfg = read_db_config()
    conn = pymysql.connect(
        host=cfg["host"], port=cfg["port"], user=cfg["DB_USERNAME"],
        password=cfg["DB_PASSWORD"], database=cfg["database"], charset="utf8mb4",
    )
    converted = skipped = 0
    try:
        with conn.cursor() as cur:
            for table, col in TARGETS.items():
                cur.execute(f"SELECT id, {col} FROM `{table}` WHERE {col} IS NOT NULL AND {col} <> ''")
                for row_id, stored in cur.fetchall():
                    if stored.startswith("$2"):
                        skipped += 1
                        continue
                    # stored 是明文：转成 BCrypt。哈希前缀 $2b$ 由 Spring Security 的
                    # BCrypt.checkpw 原生支持（$2a/$2b/$2y 都认），无需再改前缀。
                    new_hash = bcrypt.hashpw(stored.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode()
                    # 自检：新哈希必须能验过原明文（进程内验证，明文不落盘）
                    if not bcrypt.checkpw(stored.encode("utf-8"), new_hash.encode("utf-8")):
                        sys.exit(f"自检失败：{table}.id={row_id} 新哈希验不过原密码，已中止")
                    if args.dry_run:
                        print(f"  [dry-run] {table}.id={row_id} 明文密码将转 BCrypt")
                    else:
                        cur.execute(f"UPDATE `{table}` SET {col} = %s WHERE id = %s", (new_hash, row_id))
                    converted += 1
            if not args.dry_run:
                conn.commit()
    finally:
        conn.close()

    print(f"明文密码已转 BCrypt：{converted} 个（{'未写库' if args.dry_run else '已写库'}）")
    print(f"已是 BCrypt 跳过：{skipped} 个")
    print("提示：转换后用原密码登录照常；明文未打印、未记录。")


if __name__ == "__main__":
    main()
