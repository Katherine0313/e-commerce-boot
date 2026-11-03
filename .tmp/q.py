import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from db import run_sql
run_sql(sys.stdin.read())
