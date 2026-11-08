import sys
sys.path.insert(0, '/app')
from app.ai import repo

r = repo._query_all("SHOW CREATE TABLE ai_chat_role_knowledge")
print("=== ai_chat_role_knowledge ===")
print(r[0]['Create Table'] if r else 'NOT FOUND')
print()

r2 = repo._query_all("SELECT * FROM ai_chat_role_knowledge")
print("=== rows ===")
for x in r2:
    print(x)
