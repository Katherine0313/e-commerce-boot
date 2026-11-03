"""并发双击测试：两个发货请求同时打同一个 status=3 的订单。

预期：只有 1 个成功（200），另一个拿 409「订单状态已变更，请刷新后重试」，
order_delivery 只多 1 条包裹。
用法: python .tmp/race.py <orderId>
"""
import base64, hashlib, hmac, json, os, sys, time
import concurrent.futures
import requests

BASE = 'http://127.0.0.1:9104'
SECRET = os.environ.get('JWT_SECRET')
ORDER = int(sys.argv[1])


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


now = int(time.time())
header = {"alg": "HS512", "typ": "JWT"}
payload = {"sub": "9999", "type": "admin", "id": 9999, "username": "smoke-tester",
           "roles": ["operator"], "permissions": ["*:*:*"], "iat": now, "exp": now + 3600}
seg = b64(json.dumps(header, separators=(',', ':')).encode()) + '.' + b64(json.dumps(payload, separators=(',', ':')).encode())
sig = hmac.new(SECRET.encode('utf-8'), seg.encode('ascii'), hashlib.sha512).digest()
TOKEN = seg + '.' + b64(sig)
H = {'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'}


def fire(i):
    r = requests.post(BASE + '/api/admin/deliveries?orderId=%d' % ORDER, headers=H,
                      json={'expressCode': 'shunfeng', 'trackingNumber': 'RACE%s' % i},
                      timeout=30)
    try:
        body = r.json()
    except Exception:
        body = r.text
    return r.status_code, body.get('message') if isinstance(body, dict) else str(body)


with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:
    results = list(ex.map(fire, ['A', 'B']))

for code, msg in results:
    print(f'-> {code} {msg}')
codes = sorted(c for c, _ in results)
print('结果:', codes, '期望 [200, 409]')
sys.exit(0 if codes == [200, 409] else 1)
