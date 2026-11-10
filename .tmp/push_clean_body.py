"""
从本地 DB 读已清洗好的 body，生成 UPDATE SQL，上传生产执行。
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

# 从本地 DB 读已清洗好的 body（本地已干净）
ids = run_mysql("SELECT id FROM products WHERE body IS NOT NULL AND body != ''").split('\n')
print(f"本地 {len(ids)} 条")

# 同时读生产当前的 body（需要从服务器读...算了直接用本地干净值覆盖）
updates = []
for pid in ids:
    if not pid.strip(): continue
    body = run_mysql(f"SELECT body FROM products WHERE id = {pid}")
    if not body: continue
    esc = body.replace('\\', '\\\\').replace("'", "\\'")
    updates.append(f"UPDATE products SET body = '{esc}' WHERE id = {pid};")

sql_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'body_update.sql')
open(sql_path, 'w', encoding='utf-8').write('\n'.join(updates) + '\n')
print(f"生成 {len(updates)} 条 UPDATE SQL → {sql_path}")

# 上传生产
r = subprocess.run(['scp', sql_path, 'ecommerce:/tmp/body_update.sql'], capture_output=True, text=True)
if r.returncode != 0: print("上传失败:", r.stderr); sys.exit(1)
print("已上传服务器")

# 执行
remote = """
mysql -h127.0.0.1 -ue_commerce_db -p123456 --default-character-set=utf8mb4 e_commerce_db < /tmp/body_update.sql
mysql -h127.0.0.1 -ue_commerce_db -p123456 --default-character-set=utf8mb4 e_commerce_db -e "SELECT COUNT(*) AS left_p FROM products WHERE body REGEXP '<p'; SELECT LEFT(body, 150) FROM products WHERE id = 1;"
"""
r = subprocess.run(['ssh', 'ecommerce', remote], capture_output=True, text=True, timeout=120)
print(r.stdout)
if r.stderr: print("STDERR:", r.stderr[:500])

os.remove(sql_path)
