#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证物流轨迹的滚动时间轴：节点时间跟着「现在」走，不再卡在固定偏移上。"""
import json
import time
import urllib.request

ORDER_SVC = "http://127.0.0.1:9104"
USER_SVC = "http://127.0.0.1:9006"
ORDER_ID = 224
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
    if show:
        print(f"[{'OK ' if code == 200 else 'FAIL'}] {label} -> {code}")
    return payload


login = req("POST", USER_SVC + "/api/wxlogin", {"code": "dev_mock_openid_verify"},
            label="C端登录", show=False)
token = (login.get("data") or {}).get("token")
assert token, "未拿到 token"


def traces():
    vo = req("GET", f"{ORDER_SVC}/api/orders/{ORDER_ID}/logistics", token=token,
              label="取轨迹", show=False).get("data") or {}
    return vo.get("traces") or []


# 第一次：已发货（4 条）
t1 = traces()
print(f"[1] 已发货订单 {ORDER_ID} → {len(t1)} 条")
for t in t1:
    print(f"    {t.get('time')}  {t.get('status')}  {t.get('desc')[:34]}")
if len(t1) != 4:
    FAILED.append(f"已发货应为 4 条，实际 {len(t1)}")
# 已揽件应固定在真实发货时间（不随 now 滚动）
if t1 and not t1[0].get("time"):
    FAILED.append("已揽件时间为空")

# 等待 3 秒再取一次：滚动节点（now-6h 等）应跟着变
time.sleep(3)
t2 = traces()
print(f"\n[2] 3 秒后再取 → {len(t2)} 条")
for t in t2:
    print(f"    {t.get('time')}  {t.get('status')}")
if t1 and t2:
    if t1[0].get("time") != t2[0].get("time"):
        FAILED.append("已揽件时间不应变化")
    if t1[1].get("time") == t2[1].get("time"):
        FAILED.append("运输中节点时间应随 now 滚动（3 秒后仍相同）")
    else:
        print("    → 滚动节点时间已随 now 更新，已揽件保持不变 ✓")

print("\n" + ("全部通过" if not FAILED else "失败项: " + "; ".join(FAILED)))
