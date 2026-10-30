import pgserver, psycopg2, threading, uuid, pathlib
srv = pgserver.get_server('/tmp/pgdata_upnext', cleanup_mode='delete'); uri = srv.get_uri()
def conn(ac=False):
    c = psycopg2.connect(uri); c.autocommit = ac; return c
def run(p):
    c = conn(True); c.cursor().execute(pathlib.Path(p).read_text()); c.close()
B='/home/claude/upnext/db/'
for f in ['migration/V1__upnext_core.sql','migration/V100__upnext_auth.sql']: run(B+f)
print("up migrations applied")

c = conn(True); cur = c.cursor()
rid = str(uuid.uuid4()); uid = str(uuid.uuid4())
cur.execute("INSERT INTO request(id,title,description,author_id,author_name) VALUES (%s,'Dark mode','Please add a dark theme for night use',%s,'Ada')",(rid,uid))

def race(n, sql, params_fn):
    out=[]; bar=threading.Barrier(n)
    def w(i):
        cc=conn(); cu=cc.cursor(); bar.wait()
        cu.execute(sql, params_fn(i)); out.append(cu.rowcount); cc.commit(); cc.close()
    ts=[threading.Thread(target=w,args=(i,)) for i in range(n)]
    [t.start() for t in ts]; [t.join() for t in ts]; return out

# 1. same user votes 8 times at once
race(8, "INSERT INTO vote(request_id,user_id) VALUES (%s,%s) ON CONFLICT DO NOTHING", lambda i:(rid,uid))
cur.execute("SELECT count(*) FROM vote WHERE request_id=%s",(rid,)); print("WITH PK    : 8 simultaneous votes by one user ->", cur.fetchone()[0], "vote(s)")
cur.execute("DELETE FROM vote"); cur.execute("ALTER TABLE vote DROP CONSTRAINT vote_pkey")
race(8, "INSERT INTO vote(request_id,user_id) VALUES (%s,%s) ON CONFLICT DO NOTHING", lambda i:(rid,uid))
cur.execute("SELECT count(*) FROM vote WHERE request_id=%s",(rid,)); print("WITHOUT PK : 8 simultaneous votes by one user ->", cur.fetchone()[0], "vote(s)")
cur.execute("DELETE FROM vote"); cur.execute("ALTER TABLE vote ADD PRIMARY KEY (request_id,user_id)")

# 2. two admins move the same card at once (compare-and-set on version)
cur.execute("UPDATE request SET status='OPEN', version=0 WHERE id=%s",(rid,))
res = race(2, "UPDATE request SET status=%s, version=version+1, updated_at=now() WHERE id=%s AND version=0", lambda i:(['PLANNED','IN_PROGRESS'][i], rid))
print("WITH version check   : two admins, same card ->", sorted(res), "rows updated (1 = won, 0 = sees stale)")
cur.execute("UPDATE request SET status='OPEN', version=0 WHERE id=%s",(rid,))
res = race(2, "UPDATE request SET status=%s, version=version+1, updated_at=now() WHERE id=%s", lambda i:(['PLANNED','IN_PROGRESS'][i], rid))
print("WITHOUT version check:", sorted(res), "rows updated (both think they won; last write silently wins)")

# 3. case-insensitive unique email, simultaneous signups
res = race(6, "INSERT INTO app_user(email,password_hash,display_name) VALUES (%s,'x','n') ON CONFLICT DO NOTHING", lambda i:(['Sam@Example.com','sam@example.COM'][i%2],))
print("6 simultaneous signups, one email in mixed case ->", sum(res), "account created")
# 4. rules in the schema
for sql,label in [("INSERT INTO request(title,description,author_id,author_name) VALUES ('ab','long enough description',gen_random_uuid(),'x')","2-char title"),
                  ("INSERT INTO request(title,description,author_id,author_name,tags) VALUES ('Good title','long enough description',gen_random_uuid(),'x',ARRAY['a','b','c','d','e','f'])","6 tags"),
                  ("INSERT INTO request(title,description,author_id,author_name,status) VALUES ('Good title','long enough description',gen_random_uuid(),'x','DONE')","unknown status")]:
    try: cur.execute(sql); print("NOT refused:",label)
    except psycopg2.errors.CheckViolation: print("refused by CHECK:",label)
run(B+'down/V100__down.sql'); run(B+'down/V1__down.sql'); run(B+'migration/V1__upnext_core.sql'); run(B+'migration/V100__upnext_auth.sql')
print("down + up round trip: OK")
