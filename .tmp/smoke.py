"""发货管理模块本地冒烟测试：造 admin token（HS512）+ 调接口。

密钥从环境变量 JWT_SECRET 读取，不打印、不落盘。
用法: python .tmp/smoke.py
"""
import base64, hashlib, hmac, json, os, sys, time
import requests

BASE = 'http://127.0.0.1:9104'
SECRET = os.environ.get('JWT_SECRET')
if not SECRET:
    print('JWT_SECRET 未设置'); sys.exit(1)

ADMIN_ID = 9999  # 避免与真实管理员 id 撞上（Redis 令牌失效标记）


def b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


def mint(admin_id=ADMIN_ID, permissions=None, roles=None):
    now = int(time.time())
    header = {"alg": "HS512", "typ": "JWT"}
    payload = {
        "sub": str(admin_id),
        "type": "admin",
        "id": admin_id,
        "username": "smoke-tester",
        "roles": roles if roles is not None else ["operator"],
        "permissions": permissions if permissions is not None else ["*:*:*"],
        "iat": now,
        "exp": now + 3600,
    }
    seg = b64(json.dumps(header, separators=(',', ':')).encode()) + '.' + \
          b64(json.dumps(payload, separators=(',', ':')).encode())
    sig = hmac.new(SECRET.encode('utf-8'), seg.encode('ascii'), hashlib.sha512).digest()
    return seg + '.' + b64(sig)


TOKEN = mint()
H = {'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'}
FAILED = []


def call(method, path, expect=None, body=None, label=None, headers=None):
    url = BASE + path
    r = requests.request(method, url, headers=headers or H, json=body, timeout=20)
    try:
        data = r.json()
    except Exception:
        data = r.text
    tag = label or f'{method} {path}'
    status = 'OK ' if (expect is None or r.status_code == expect) else 'FAIL'
    if status == 'FAIL':
        FAILED.append(tag + f'(期望{expect} 实际{r.status_code})')
    print(f'[{status}] {tag} -> {r.status_code} {json.dumps(data, ensure_ascii=False)[:700] if isinstance(data, (dict, list)) else str(data)[:700]}')
    return r, data


def section(title):
    print('\n===== ' + title + ' =====')


ORDER = 218

# ---------- 1. 快递公司字典 ----------
section('1 快递公司字典')
call('GET', '/api/admin/expresses?page=1&pageSize=20', 200, label='快递公司列表')
call('GET', '/api/admin/expresses/enabled', 200, label='启用中的快递公司')

# ---------- 2. 包裹列表（空库） ----------
section('2 包裹列表')
call('GET', '/api/admin/deliveries?page=1&pageSize=10', 200, label='包裹列表(空)')
call('GET', '/api/admin/orders/couriers', 200, label='订单页快递公司(旧接口改读字典)')

# ---------- 3. 异常路径：状态 1（待付款）时发货 ----------
# 注：原先这里测的是「待确认(2)」，该状态已于 2026-11-04 退役并入 3
section('3 异常路径（状态1）')
call('POST', '/api/admin/deliveries?orderId=%d' % ORDER, 400,
     body={'expressCode': 'shunfeng', 'trackingNumber': 'SF123456789'}, label='待付款(1)发货应400')
call('POST', '/api/admin/deliveries?orderId=999999', 404,
     body={'expressCode': 'shunfeng', 'trackingNumber': 'SF123456789'}, label='订单不存在应404')

# ---------- 4. 进入待发货(3) ----------
# 原先是「确认收款 2→3」，确认收款环节已取消；现在支付成功直接进 3（PUT /api/orders/{id}/pay 是 C 端接口，
# 冒烟脚本用 admin token 调不了），这里改为脚本外用 SQL 把订单预置成 status=3。

# ---------- 5. 参数校验 ----------
section('5 参数校验（状态3）')
call('POST', '/api/admin/deliveries?orderId=%d' % ORDER, 400,
     body={'expressCode': '', 'trackingNumber': 'SF123456789'}, label='空快递公司应400')
call('POST', '/api/admin/deliveries?orderId=%d' % ORDER, 400,
     body={'expressCode': 'shunfeng', 'trackingNumber': ''}, label='空单号应400')
call('POST', '/api/admin/deliveries?orderId=%d' % ORDER, 400,
     body={'expressCode': 'JD', 'trackingNumber': 'JD000111'}, label='未在字典的快递公司应400')

# ---------- 6. 发货 ----------
section('6 发货 3→4')
r, data = call('POST', '/api/admin/deliveries?orderId=%d' % ORDER, 200,
               body={'expressCode': 'shunfeng', 'trackingNumber': 'SF123456789', 'remark': '冒烟测试'},
               label='发货')
delivery_id = (data.get('data') or {}).get('id')
package_no = (data.get('data') or {}).get('packageNo')

# ---------- 7. 重复发货（并发双击的第二次） ----------
section('7 重复发货')
call('POST', '/api/admin/deliveries?orderId=%d' % ORDER, 400,
     body={'expressCode': 'shunfeng', 'trackingNumber': 'SF123456789'}, label='已发货再发货应400（不得产生第二个包裹）')

# ---------- 8. 包裹列表 / 流水 ----------
section('8 列表与流水')
call('GET', '/api/admin/deliveries?page=1&pageSize=10', 200, label='包裹列表(有数据)')
call('GET', '/api/admin/deliveries/logs?orderId=%d' % ORDER, 200, label='发货流水')
call('GET', '/api/admin/orders/%d' % ORDER, 200, label='订单详情')
call('GET', '/api/admin/orders?page=1&pageSize=5&status=4', 200, label='已发货订单列表')
call('GET', '/api/admin/orders/status-constants', 200, label='状态常量')

# ---------- 9. 改快递信息 ----------
section('9 改快递信息')
if delivery_id:
    call('PUT', '/api/admin/deliveries/%d' % delivery_id, 200,
         body={'expressCode': 'zhongtong', 'trackingNumber': 'ZT987654321', 'remark': '改单号'},
         label='修改包裹快递信息')

# ---------- 10. 补发 ----------
section('10 补发')
call('POST', '/api/admin/deliveries/resend?orderId=%d' % ORDER, 200,
     body={'expressCode': 'yuantong', 'trackingNumber': 'YT555666777', 'remark': '补发'},
     label='补发')
call('POST', '/api/admin/deliveries/resend?orderId=999999', 404,
     body={'expressCode': 'yuantong', 'trackingNumber': 'YT555666777'}, label='订单不存在补发应404')

# ---------- 11. 权限：无权限码应 403 ----------
section('11 权限校验')
H2 = {'Authorization': 'Bearer ' + mint(permissions=['order:order:list']), 'Content-Type': 'application/json'}
r = requests.get(BASE + '/api/admin/deliveries', headers=H2, timeout=20)
print(f'[{"OK " if r.status_code == 403 else "FAIL"}] 无 order:delivery:list 权限 -> {r.status_code} {r.text[:200]}')
if r.status_code != 403:
    FAILED.append('权限403')

print('\n===== 汇总 =====')
print('失败项:', FAILED if FAILED else '无')
print('delivery_id =', delivery_id, ' package_no =', package_no)
sys.exit(1 if FAILED else 0)
