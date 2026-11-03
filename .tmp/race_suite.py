"""并发双击回归：对多个订单各跑一次并发发货，期望 [200, 409] 且只产生 1 个包裹。

顺带覆盖混合场景：
  A. 两个并发发货           -> [200, 409]
  B. 发货 + 补发并发         -> 不死锁（200/200 或 200/400 均可），不能 500
  C. 两个并发改快递信息      -> 不死锁（200/200 或 200/409 均可），不能 500

用法: python .tmp/race_suite.py
"""
import base64, concurrent.futures, hashlib, hmac, json, os, subprocess, sys, time
import requests

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from db import run_sql

BASE = 'http://127.0.0.1:9104'
SECRET = os.environ.get('JWT_SECRET')
ADMIN_ID = 9999


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


def mint():
    now = int(time.time())
    header = {"alg": "HS512", "typ": "JWT"}
    payload = {"sub": str(ADMIN_ID), "type": "admin", "id": ADMIN_ID, "username": "smoke-tester",
               "roles": ["operator"], "permissions": ["*:*:*"], "iat": now, "exp": now + 3600}
    seg = b64(json.dumps(header, separators=(',', ':')).encode()) + '.' + b64(json.dumps(payload, separators=(',', ':')).encode())
    sig = hmac.new(SECRET.encode('utf-8'), seg.encode('ascii'), hashlib.sha512).digest()
    return seg + '.' + b64(sig)


H = {'Authorization': 'Bearer ' + mint(), 'Content-Type': 'application/json'}
FAILED = []


def sql(stmt):
    return run_sql(stmt, show=False)


def post(path, body):
    r = requests.post(BASE + path, headers=H, json=body, timeout=30)
    try:
        d = r.json()
    except Exception:
        d = {}
    return r.status_code, (d.get('message') if isinstance(d, dict) else '')


def put(path, body):
    r = requests.put(BASE + path, headers=H, json=body, timeout=30)
    try:
        d = r.json()
    except Exception:
        d = {}
    return r.status_code, (d.get('message') if isinstance(d, dict) else '')


def parallel(fn, *args):
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(args)) as ex:
        return list(ex.map(lambda a: fn(*a), args))


def set_status(order_id, status=3):
    sql(f"UPDATE orders SET order_status = {status}, courier = NULL, tracking_number = NULL, "
        f"ship_at = NULL, confirm_at = NOW(), sign_status = 'unsigned', updated_at = NOW() "
        f"WHERE id = {order_id} AND is_deleted = 0;")


def pkg_count(order_id):
    out = sql(f"SELECT COUNT(*) FROM order_delivery WHERE order_id = {order_id};")
    return int(out.strip().splitlines()[-1].split('\t')[-1])


def case_a(order_id, tag):
    set_status(order_id, 3)
    before = pkg_count(order_id)
    res = parallel(post, *[('/api/admin/deliveries?orderId=%d' % order_id,
                             {'expressCode': 'shunfeng', 'trackingNumber': 'R%s%d' % (c, order_id)})
                            for c in ('A', 'B')])
    codes = sorted(c for c, _ in res)
    after = pkg_count(order_id)
    ok = codes == [200, 409] and after == before + 1
    print(f'[{ "OK " if ok else "FAIL" }] A 并发发货 {tag} order={order_id} -> {codes} 包裹 {before}->{after} {[m for _, m in res]}')
    if not ok:
        FAILED.append(f'A/{tag}/{order_id} {codes} pkg{before}->{after}')


def case_b(order_id, tag):
    """发货 与 补发 并发（补发要求订单已发货，这里对已发货订单并发补发两次）"""
    set_status(order_id, 3)
    sql(f"UPDATE orders SET order_status = 4, courier = 'shunfeng', tracking_number = 'PRE', "
        f"ship_at = NOW(), updated_at = NOW() WHERE id = {order_id} AND is_deleted = 0;")
    before = pkg_count(order_id)
    res = parallel(post, *[('/api/admin/deliveries/resend?orderId=%d' % order_id,
                             {'expressCode': 'yuantong', 'trackingNumber': 'RS%s%d' % (c, order_id)})
                            for c in ('A', 'B')])
    codes = sorted(c for c, _ in res)
    after = pkg_count(order_id)
    ok = 500 not in codes and after == before + 2
    print(f'[{"OK " if ok else "FAIL"}] B 并发补发 {tag} order={order_id} -> {codes} 包裹 {before}->{after} {[m for _, m in res]}')
    if not ok:
        FAILED.append(f'B/{tag}/{order_id} {codes} pkg{before}->{after}')


def case_c(order_id, tag):
    """两个并发改同一个包裹的快递信息"""
    set_status(order_id, 3)
    sql(f"UPDATE orders SET order_status = 4, courier = 'shunfeng', tracking_number = 'PRE', "
        f"ship_at = NOW(), updated_at = NOW() WHERE id = {order_id} AND is_deleted = 0;")
    sql(f"DELETE FROM order_delivery_log WHERE order_id = {order_id};")
    sql(f"DELETE FROM order_delivery WHERE order_id = {order_id};")
    sql(f"INSERT INTO order_delivery (order_id, order_no, package_no, express_code, express_name, "
        f"tracking_number, delivery_type, delivery_time, remark, is_deleted, created_at) "
        f"SELECT id, order_no, 'PKGC{order_id}', 'shunfeng', '顺丰速运', 'OLDNO{order_id}', 1, NOW(), NULL, 0, NOW() "
        f"FROM orders WHERE id = {order_id};")
    did = sql(f"SELECT id FROM order_delivery WHERE order_id = {order_id};").strip().splitlines()[-1].split('\t')[-1]
    res = parallel(put, *[('/api/admin/deliveries/%s' % did,
                           {'expressCode': 'zhongtong', 'trackingNumber': 'NEW%s%d' % (c, order_id)})
                          for c in ('A', 'B')])
    codes = sorted(c for c, _ in res)
    ok = 500 not in codes
    print(f'[{"OK " if ok else "FAIL"}] C 并发改单号 {tag} order={order_id} -> {codes} {[m for _, m in res]}')
    if not ok:
        FAILED.append(f'C/{tag}/{order_id} {codes}')


print('=== 并发回归 ===')
for i, oid in enumerate([211, 209, 207, 206, 205, 204]):
    case_a(oid, '并发发货')
for i, oid in enumerate([203, 202, 200]):
    case_b(oid, '并发补发')
for i, oid in enumerate([182, 181]):
    case_c(oid, '并发改单号')

print('\n失败项:', FAILED if FAILED else '无')
sys.exit(1 if FAILED else 0)
