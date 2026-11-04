#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证后台 / C 端（小程序、PC 商城共用）两边的物流轨迹完全同步。token 不打印。"""
import json
import subprocess
import urllib.request

ORDER_SVC = "http://127.0.0.1:9104"
AUTH_SVC = "http://127.0.0.1:9001"
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
        FAILED.append(f"{label}(实际 {code}: {payload.get('message')})")
    if show:
        print(f"[{'OK ' if code == 200 else 'FAIL'}] {label} -> {code}")
    return payload


# ---------- 后台管理员 token ----------
cap = req("GET", AUTH_SVC + "/api/auth/captcha", label="取验证码")
captcha_id = (cap.get("data") or {}).get("captchaId")
code = subprocess.check_output(
    ["redis-cli", "-h", "127.0.0.1", "GET", "auth:captcha:" + captcha_id], text=True).strip()
login = req("POST", AUTH_SVC + "/api/login",
            {"username": "admin", "password": "admin123",
             "captchaId": captcha_id, "captchaCode": code},
            label="管理员登录", show=False)
admin_token = (login.get("data") or {}).get("token")
assert admin_token, "未拿到管理员 token"

# ---------- C 端 token ----------
u = req("POST", USER_SVC + "/api/wxlogin", {"code": "dev_mock_openid_verify"},
        label="C端登录", show=False)
user_token = (u.get("data") or {}).get("token")
assert user_token, "未拿到 C 端 token"


def logistics(url, token, label):
    vo = req("GET", url, token=token, label=label, show=False).get("data") or {}
    print(f"    快递公司={vo.get('courier')}  单号={vo.get('trackingNumber')}  轨迹 {len(vo.get('traces') or [])} 条")
    for t in (vo.get("traces") or []):
        print(f"      {t.get('time')}  {t.get('status')}")
    return vo


print("\n--- 后台视角 ---")
admin_vo = logistics(f"{ORDER_SVC}/api/admin/orders/{ORDER_ID}/logistics", admin_token, "后台物流")
print("\n--- C 端视角（小程序 / PC 商城）---")
user_vo = logistics(f"{ORDER_SVC}/api/orders/{ORDER_ID}/logistics", user_token, "C端物流")

# 对比：快递公司、单号、轨迹条数、每条的状态与文案（时间戳允许差 1 秒内的 now 抖动）
print("\n--- 对比 ---")
if admin_vo.get("courier") != user_vo.get("courier"):
    FAILED.append(f"快递公司不一致: {admin_vo.get('courier')} vs {user_vo.get('courier')}")
else:
    print(f"  快递公司一致: {admin_vo.get('courier')}")
if admin_vo.get("trackingNumber") != user_vo.get("trackingNumber"):
    FAILED.append("快递单号不一致")
else:
    print(f"  快递单号一致: {admin_vo.get('trackingNumber')}")

a = admin_vo.get("traces") or []
b = user_vo.get("traces") or []
if len(a) != len(b):
    FAILED.append(f"轨迹条数不一致: {len(a)} vs {len(b)}")
else:
    print(f"  轨迹条数一致: {len(a)}")
for i, (x, y) in enumerate(zip(a, b)):
    if x.get("status") != y.get("status") or x.get("desc") != y.get("desc"):
        FAILED.append(f"第 {i + 1} 条轨迹不一致: {x} vs {y}")
print("  每条轨迹的状态与文案一致" if not FAILED else "  轨迹内容有差异")

print("\n" + ("全部通过" if not FAILED else "失败项: " + "; ".join(FAILED)))
