"""商品规格参数（specs）后台接口冒烟：列表回显 / 更新 / 清空 / 新增。

密钥从环境变量 JWT_SECRET 读取，不打印、不落盘。
用法: python .tmp/specs_smoke.py
"""
import base64, hashlib, hmac, json, os, sys, time
import requests

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from db import run_sql

BASE = 'http://127.0.0.1:9105'
SECRET = os.environ.get('JWT_SECRET')
FAILED = []


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


def mint():
    now = int(time.time())
    header = {"alg": "HS512", "typ": "JWT"}
    payload = {"sub": "9999", "type": "admin", "id": 9999, "username": "smoke-tester",
               "roles": ["operator"], "permissions": ["*:*:*"], "iat": now, "exp": now + 3600}
    seg = b64(json.dumps(header, separators=(',', ':')).encode()) + '.' + b64(json.dumps(payload, separators=(',', ':')).encode())
    sig = hmac.new(SECRET.encode('utf-8'), seg.encode('ascii'), hashlib.sha512).digest()
    return seg + '.' + b64(sig)


H = {'Authorization': 'Bearer ' + mint(), 'Content-Type': 'application/json'}
PRODUCT_ID = 1


def check(label, cond, detail=''):
    print(f'[{"OK " if cond else "FAIL"}] {label} {detail}')
    if not cond:
        FAILED.append(label)


def sql(stmt):
    # mysql 客户端默认带表头，取最后一行即结果值
    out = run_sql(stmt, show=False).strip()
    return out.splitlines()[-1].strip() if out else ''


def specs_of(order_id=PRODUCT_ID):
    return sql(f"SELECT specs FROM products WHERE id = {PRODUCT_ID};")


print('=== 1 列表接口是否回显 specs 数组 ===')
r = requests.get(BASE + '/api/admin/products?page=1&pageSize=5', headers=H, timeout=20)
body = r.json()
rows = body.get('data', {}).get('list', [])
check('列表接口 200', r.status_code == 200, f'-> {r.status_code}')
row = next((x for x in rows if x['id'] == PRODUCT_ID), None)
check('列表行含 specs 字段', row is not None and 'specs' in row, f'行={row is not None}')
if row:
    check('specs 是数组（不是 JSON 字符串）', isinstance(row['specs'], list),
          f'类型={type(row["specs"]).__name__} 样例={json.dumps(row["specs"], ensure_ascii=False)[:120]}')
    check('specs 项含 name/value', all(set(('name', 'value')) <= set(i) for i in row['specs']),
          f'首项={json.dumps(row["specs"][0], ensure_ascii=False) if row["specs"] else "空"}')

print('\n=== 2 商品详情接口 ===')
r = requests.get(BASE + f'/api/admin/products/{PRODUCT_ID}', headers=H, timeout=20)
d = r.json().get('data', {})
check('详情 200 且 specs 是数组', r.status_code == 200 and isinstance(d.get('specs'), list),
      f'-> {r.status_code} {json.dumps(d.get("specs"), ensure_ascii=False)[:120]}')

print('\n=== 3 更新规格参数 ===')
before = specs_of()
new_specs = [{'name': '屏幕尺寸', 'value': '6.1英寸'}, {'name': '处理器', 'value': 'A17 Pro'},
             {'name': '冒烟测试项', 'value': '临时'}]
r = requests.put(BASE + f'/api/admin/products/{PRODUCT_ID}', headers=H,
                 json={'specs': new_specs}, timeout=20)
check('更新 200', r.status_code == 200, f'-> {r.status_code} {r.text[:160]}')
after = specs_of()
check('库里 specs 已改成新值', json.loads(after) == new_specs, f'-> {after[:160]}')

print('\n=== 4 参数名校验（后端拦截） ===')
r = requests.put(BASE + f'/api/admin/products/{PRODUCT_ID}', headers=H,
                 json={'specs': [{'name': '', 'value': 'x'}]}, timeout=20)
check('空参数名应 400', r.status_code == 400, f'-> {r.status_code} {r.text[:160]}')

print('\n=== 5 传空数组表示清空 ===')
r = requests.put(BASE + f'/api/admin/products/{PRODUCT_ID}', headers=H, json={'specs': []}, timeout=20)
check('清空 200', r.status_code == 200, f'-> {r.status_code}')
check('库里 specs 已清空', specs_of() == '[]', f'-> {specs_of()}')

print('\n=== 6 新增商品带规格参数 ===')
payload = {'name': '冒烟测试商品', 'categoryId': 101, 'brand': '测试品牌', 'subTitle': '副标题',
           'salesPrice': 1.0, 'marketPrice': 2.0, 'stock': 1, 'specs': [{'name': '颜色', 'value': '红'}]}
r = requests.post(BASE + '/api/admin/products', headers=H, json=payload, timeout=20)
check('新增 200', r.status_code == 200, f'-> {r.status_code} {r.text[:200]}')
new_id = None
if r.status_code == 200:
    new_id = r.json()['data']['id']
    check('新增返回的 specs 正确',
          r.json()['data']['specs'] == [{'name': '颜色', 'value': '红'}],
          f'-> {json.dumps(r.json()["data"]["specs"], ensure_ascii=False)}')

print('\n=== 7 还原 ===')
if new_id:
    requests.delete(BASE + f'/api/admin/products/{new_id}', headers=H, timeout=20)
    check('测试商品已删除', f"SELECT COUNT(*) FROM products WHERE id = {new_id};" == '0', '')
sql(f"UPDATE products SET specs = '{before.replace(chr(39), chr(39)*2)}' WHERE id = {PRODUCT_ID};")
check('原商品 specs 已还原', specs_of() == before, f'-> {specs_of()[:120]}')

print('\n失败项:', FAILED if FAILED else '无')
sys.exit(1 if FAILED else 0)
