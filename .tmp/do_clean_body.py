import pymysql, re, os

# 服务器本机连宝塔 MySQL，固定 127.0.0.1
env = {}
with open('/www/java/.env') as f:
    for line in f:
        line = line.strip()
        if line and '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            env[k.strip()] = v.strip()

user = env.get('DB_USERNAME', 'root')
pwd  = env.get('DB_PASSWORD', '')
# 从 DB_URL 提取 db 名
url = env.get('DB_URL', '')
m = re.search(r'/([^?]+)', url)
db = m.group(1) if m else 'e_commerce_db'

print(f"连接 MySQL 127.0.0.1 user={user} db={db}")

conn = pymysql.connect(host='127.0.0.1', port=3306, user=user, password=pwd, database=db, charset='utf8mb4')
cur = conn.cursor()

cur.execute("SELECT id, body FROM products WHERE body IS NOT NULL AND body != ''")
rows = cur.fetchall()
print(f"生产环境：共 {len(rows)} 条 body 非空")

param_block = re.compile(r'<p[^>]*>\s*<strong>\s*产品参数\s*</strong>\s*</p>[\s\S]*?(?=<img|$)', re.IGNORECASE)
para_re = re.compile(r'<p\b[^>]*>[\s\S]*?</p>', re.IGNORECASE)

def clean(body):
    body = param_block.sub('', body)
    body = para_re.sub(lambda mm: mm.group(0) if re.search(r'<img', mm.group(0), re.IGNORECASE) else '', body)
    body = re.sub(r'<p[^>]*>\s*</p>', '', body, flags=re.IGNORECASE)
    body = re.sub(r'\s*</p>', '', body)
    body = body.strip()
    return body

updated = 0
for pid, body in rows:
    new = clean(body)
    if new != body:
        cur.execute('UPDATE products SET body = %s WHERE id = %s', (new, pid))
        updated += 1
conn.commit()

cur.execute("SELECT COUNT(*) FROM products WHERE body REGEXP '<p'")
left = cur.fetchone()[0]
print(f"更新 {updated} 条，剩余含 <p> = {left}")

cur.execute("SELECT LEFT(body, 200) FROM products WHERE id = 1")
print("--- 抽样 id=1 ---")
print(cur.fetchone()[0])

conn.close()
print("生产清洗完成")
