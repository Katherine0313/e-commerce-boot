#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证后台「查看物流」用的接口能返回非空轨迹（假数据）。只打印断言结果，不打印 token。"""
import json
import urllib.request

ORDER_SVC = "http://127.0.0.1:9104"
USER_SVC = "http://127.0.0.1:9006"
FAILED = []


def req(method, url, body=None, token=None, label="", show=True):
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
    if code != 200:
        FAILED.append(f"{label}(实际 {code})")
    shown = json.dumps(payload, ensure_ascii=False)[:400] if show else "<已隐藏>"
    print(f"[{'OK ' if code == 200 else 'FAIL'}] {label} -> {code} {shown}")
    return payload


login = req("POST", USER_SVC + "/api/wxlogin", {"code": "dev_mock_openid_verify"},
            label="C端登录", show=False)
token = (login.get("data") or {}).get("token")
assert token, "未拿到 token"

# 已发货订单 → 后端按「已发生的时间」拼轨迹（刚发货只有 1 条，发货越久条数越多；
# C 端接口最多 4 条，后台接口 5 条，已完成再多 2 条派送/签收）
for order_id, expect_min in ((224, 1),):
    data = req("GET", f"{ORDER_SVC}/api/orders/{order_id}/logistics", token=token,
               label=f"订单 {order_id} 物流轨迹")
    vo = data.get("data") or {}
    traces = vo.get("traces") or []
    print(f"    快递公司={vo.get('courier')} 单号={vo.get('trackingNumber')} 轨迹条数={len(traces)}")
    if len(traces) < expect_min:
        FAILED.append(f"订单 {order_id} 轨迹条数应 >= {expect_min}，实际 {len(traces)}")
    for t in traces:
        if not t.get("time") or not t.get("status") or not t.get("desc"):
            FAILED.append(f"轨迹节点字段缺失: {t}")
            break

print("\n" + ("全部通过" if not FAILED else "失败项: " + "; ".join(FAILED)))
