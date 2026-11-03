import os, re, subprocess, sys, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
YML = os.path.join(ROOT, 'order-service', 'src', 'main', 'resources', 'application-dev.yml')

def read_cfg():
    txt = open(YML, encoding='utf-8').read()
    out = {}
    for k in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD'):
        m = re.search(rf"^{k}:\s*'?([^'\n]+)'?\s*$", txt, re.M)
        out[k] = m.group(1).strip()
    return out

def run_sql(sql, database='e_commerce_db', show=True):
    cfg = read_cfg()
    url = cfg['DB_URL']
    m = re.search(r'jdbc:mysql://([^:/]+):(\d+)/([^?]+)', url)
    host, port, db = m.group(1), int(m.group(2)), m.group(3)
    if database:
        db = database
    env = dict(os.environ)
    env['MYSQL_PWD'] = cfg['DB_PASSWORD']
    with tempfile.NamedTemporaryFile('w', suffix='.sql', delete=False, encoding='utf-8') as f:
        f.write(sql)
        path = f.name
    try:
        p = subprocess.run(['mysql', '-h', host, '-P', str(port), '-u', cfg['DB_USERNAME'],
                            '--default-character-set=utf8mb4', db],
                           env=env, stdin=open(path, encoding='utf-8'),
                           capture_output=True, text=True, encoding='utf-8', errors='replace')
        if show and p.stdout.strip():
            print(p.stdout)
        if p.returncode != 0:
            print('STDERR:', p.stderr)
            sys.exit(1)
        return p.stdout
    finally:
        os.unlink(path)

if __name__ == '__main__':
    sql = sys.stdin.read()
    run_sql(sql, database=sys.argv[1] if len(sys.argv) > 1 else None)
