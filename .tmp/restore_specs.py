"""把本地 products.id=1 的 specs 还原成生产库的原始值（冒烟测试误改的）。"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from db import run_sql

ORIGINAL = [
    {"name": "屏幕尺寸", "value": "6.1英寸"},
    {"name": "处理器", "value": "A17 Pro"},
    {"name": "运行内存", "value": "8GB"},
    {"name": "机身存储", "value": "128GB/256GB/512GB/1TB"},
    {"name": "后置摄像头", "value": "4800万像素主摄+1200万像素超广角+1200万像素3倍长焦"},
    {"name": "防水", "value": "IP68"},
]
value = json.dumps(ORIGINAL, ensure_ascii=False)
run_sql("UPDATE products SET specs = '%s' WHERE id = 1;" % value.replace("'", "''"), show=False)
print(run_sql("SELECT specs FROM products WHERE id = 1;", show=False))
