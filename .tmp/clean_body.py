"""
products.body 清洗 v2：删除所有段落内部不含 <img 的段落。
用 re.sub + 回调函数判断每个 <p>...</p> 内部是否含 <img。
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
YML = os.path.join(ROOT, 'order-service', 'src', 'main', 'resources', 'application-dev.yml')

def read_cfg():
    txt = open(YML, encoding='utf-8').read()
    out = {}
    for k in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD'):
        m = re.search(rf"^{k}:\s*'?([^'\n]+)'?\s*$", txt, re.M)
        out[k] = m.group(1).strip()
    return out

cfg = read_cfg()
url = cfg['DB_URL']
m = re.search(r'jdbc:mysql://([^:/]+):(\d+)/([^?]+)', url)
host, port, db = m.group(1), int(m.group(2)), m.group(3)
user = cfg['DB_USERNAME']
pwd  = cfg['DB_PASSWORD']

def run_mysql(sql):
    env = dict(os.environ)
    env['MYSQL_PWD'] = pwd
    proc = subprocess.run(
        ['mysql', f'-h{host}', f'-P{port}', f'-u{user}', db, '--default-character-set=utf8mb4', '-N', '-B'],
        input=sql, capture_output=True, text=True, env=env
    )
    if proc.returncode != 0:
        print("ERROR:", proc.stderr); sys.exit(1)
    return proc.stdout.strip()

# 取所有 body 非空的商品
rows_text = run_mysql("SELECT id, body FROM products WHERE body IS NOT NULL AND body != ''")
# body 可能含 \n，用特殊分隔符：先拿 ID 列表
id_list = run_mysql("SELECT id FROM products WHERE body IS NOT NULL AND body != ''")
ids = [x for x in id_list.split('\n') if x.strip()]
print(f"共 {len(ids)} 条 body 非空")

# 先剥"产品参数"整块
param_block = re.compile(
    r'<p[^>]*>\s*<strong>\s*产品参数\s*</strong>\s*</p>[\s\S]*?(?=<img|$)', re.IGNORECASE
)

# 匹配单个 <p>...</p> 段落（非贪婪）
para_re = re.compile(r'<p\b[^>]*>[\s\S]*?</p>', re.IGNORECASE)

def clean_body(body):
    # 1. 剥参数区
    body = param_block.sub('', body)
    # 2. 删除内部不含 <img 的 <p> 段落
    def drop_if_no_img(m):
        seg = m.group(0)
        if re.search(r'<img', seg, re.IGNORECASE):
            return seg  # 段落里有图 → 保留
        return ''       # 没图 → 删掉
    body = para_re.sub(drop_if_no_img, body)
    # 3. 清理可能残留的空标签 / 孤立的 </p>
    body = re.sub(r'<p[^>]*>\s*</p>', '', body, flags=re.IGNORECASE)
    body = re.sub(r'\s*</p>', '', body)  # 残留 </p>
    # 4. 清理多余空行
    body = re.sub(r'\n\s*\n', '\n', body).strip()
    return body

updates = []
updated = 0
for pid in ids:
    body = run_mysql(f"SELECT body FROM products WHERE id = {pid}")
    # body 可能是 None 或空
    if not body: continue
    new = clean_body(body)
    if new != body:
        esc = new.replace('\\', '\\\\').replace("'", "\\'")
        updates.append(f"UPDATE products SET body = '{esc}' WHERE id = {pid};")
        updated += 1

print(f"将更新 {updated} 条")

if updates:
    sql_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'clean_body.sql')
    open(sql_path, 'w', encoding='utf-8').write('\n'.join(updates) + '\n')
    env = dict(os.environ)
    env['MYSQL_PWD'] = pwd
    proc = subprocess.run(
        ['mysql', f'-h{host}', f'-P{port}', f'-u{user}', db, '--default-character-set=utf8mb4'],
        input=open(sql_path, encoding='utf-8').read(), capture_output=True, text=True, env=env
    )
    if proc.returncode != 0:
        print("执行 SQL 失败:", proc.stderr); sys.exit(1)
    print("执行成功")
    os.remove(sql_path)

# 验证
print("\n--- 抽样 id=1 ---")
print(run_mysql("SELECT body FROM products WHERE id = 1"))
print("\n--- 抽样 id=2 ---")
print(run_mysql("SELECT body FROM products WHERE id = 2"))
print("\n--- 剩余含 <p> 的 body 数 ---")
print(run_mysql("SELECT COUNT(*) FROM products WHERE body REGEXP '<p'"))
