#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证「待确认收款(2) 退役 → 支付直接进待发货(3)」的端到端行为（本地临时实例 order-service:9104）。
只打印断言结果，不打印 token。
"""
import json
import urllib.request

ORDER_SVC = "http://127.0.0.1:9104"
USER_SVC = "http://127.0.0.1:9006"
PRODUCT_SVC = "http://127.0.0.1:9003"

FAILED = []


def req(method, url, body=None, token=None, expect=None, label="", show=True):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            payload = json.loads(resp.read().decode())
            code = resp.status
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode() or "{}")
        code = e.code
    ok = (expect is None) or (code == expect)
    if not ok:
        FAILED.append(f"{label}(期望{expect} 实际{code})")
    shown = json.dumps(payload, ensure_ascii=False)[:220] if show else "<已隐藏>"
    print(f"[{'OK ' if ok else 'FAIL'}] {label} -> {code} {shown}")
    return payload


# 1. C 端静默登录（token 不打印）
login = req("POST", USER_SVC + "/api/wxlogin", {"code": "dev_mock_openid_status_merge_test"}, expect=200, label="C端登录", show=False)
token = (login.get("data") or {}).get("token")
assert token, "未拿到 token"

# 2. 找一个有库存的商品
plist = req("GET", PRODUCT_SVC + "/api/products?page=1&pageSize=5", expect=200, label="商品列表")
data = plist.get("data")
items = data if isinstance(data, list) else ((data or {}).get("list") or (data or {}).get("records") or [])
product = next((p for p in items if (p.get("stock") or 0) >= 1), None)
if not product:
    print("没有有库存的商品，跳过下单验证")
    raise SystemExit(1)

# 3. 下单 → 初始状态应为 1（待付款）
created = req("POST", ORDER_SVC + "/api/orders", {
    "items": [{"productId": product["id"], "quantity": 1}],
    "consignee": "状态合并测试", "phone": "13800000000",
    "region": "浙江省 杭州市", "detailAddress": "测试地址 1 号",
    "idempotencyKey": "status-merge-verify-%d" % product["id"],
}, token=token, expect=200, label="下单")
order_id = (created.get("data") or {}).get("id")
assert order_id, "下单失败"
print(f"    订单 id={order_id} 下单后状态={created['data'].get('orderStatus')} 文案={created['data'].get('orderStatusDesc')}")
assert created["data"].get("orderStatus") == 1, "下单后状态不是 1 待付款"

# 4. 支付 → 应直接进入 3（待发货），并写入 pay_at
paid = req("PUT", f"{ORDER_SVC}/api/orders/{order_id}/pay", {"payType": "WeChat"}, token=token, expect=200, label="支付")
st = (paid.get("data") or {}).get("orderStatus")
desc = (paid.get("data") or {}).get("orderStatusDesc")
print(f"    支付后状态={st} 文案={desc} payAt={(paid.get('data') or {}).get('payAt')} confirmAt={(paid.get('data') or {}).get('confirmAt')}")
if st != 3:
    FAILED.append(f"支付后状态应为 3，实际 {st}")
if desc != "待发货":
    FAILED.append(f"支付后文案应为「待发货」，实际 {desc}")
if not (paid.get("data") or {}).get("payAt"):
    FAILED.append("支付后 payAt 为空（营业额统计会归零）")

# 5. 旧的确认收款接口应已不存在（404）
req("PUT", f"{ORDER_SVC}/api/orders/{order_id}/confirm", token=token, expect=404, label="旧确认收款接口应已删除")

# 6. 待发货订单可以直接发货（走后台接口需要 admin token，这里只验证状态机描述）
print("\n" + ("全部通过" if not FAILED else "失败项: " + "; ".join(FAILED)))
