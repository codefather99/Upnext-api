"""Runs every statement from Sql.java on a real Postgres and exercises the rules, including the races.
Usage: python verify_upnext_sql.py <Sql.java> <migrations dir>"""
import sys, re, threading, uuid, pathlib
import pgserver, psycopg2
JAVA, MIG = sys.argv[1], sys.argv[2]
def load_sql(path):
    src = pathlib.Path(path).read_text(); out = {}
    for m in re.finditer(r'static final String (\w+) =\s*', src):
        i = m.end()
        if src.startswith('"""', i):
            j = src.index('"""', i + 3); body = src[i + 3:j]; body = body.split('\n', 1)[1] if body.split('\n', 1)[0].strip() == '' else body
        else:
            j = i + 1
            while src[j] != '"': j += 2 if src[j] == '\\' else 1
            body = src[i + 1:j]
        out[m.group(1)] = body.replace('\\\\', '\\').replace('?', '%s')
    return out
S = load_sql(JAVA); print(f"loaded {len(S)} statements")
srv = pgserver.get_server('/tmp/pgdata_up', cleanup_mode='delete'); URI = srv.get_uri()
def conn(ac=False): c = psycopg2.connect(URI); c.autocommit = ac; return c
def q(sql, p=None):
    c = conn(True); cur = c.cursor(); cur.execute(sql, p); r = cur.fetchall() if cur.description else None; c.close(); return r
for f in sorted(pathlib.Path(MIG).glob('V*.sql'), key=lambda p: int(re.match(r'V(\d+)', p.name).group(1))): q(f.read_text())
ok = 0
def check(c, m):
    global ok; ok += 1
    if not c: raise AssertionError('FAIL: ' + m)
    print('  ok ', m)
NIL = '00000000-0000-0000-0000-000000000000'
print("Users")
uid = q(S['INSERT_USER'], ('Sam@Example.com', 'h', 'Sam', 'USER'))[0][0]
try: q(S['INSERT_USER'], ('sam@example.COM', 'h', 'Sam2', 'USER')); check(False, 'dup email')
except psycopg2.errors.UniqueViolation: check(True, "same email in another letter case is refused (EMAIL_TAKEN)")
check(q(S['USER_BY_EMAIL'], ('SAM@example.com',))[0][3] == 'Sam', "login lookup ignores case")
q(S['INSERT_USER_IF_MISSING'], ('sam@example.com', 'h', 'x', 'USER')); check(q("select count(*) from app_user")[0][0] == 1, "seeding a user twice is harmless")
voters = [q(S['INSERT_USER'], (f'v{i}@x.com', 'h', f'Voter {i}', 'USER'))[0][0] for i in range(4)]
admin = q(S['INSERT_USER'], ('admin@x.com', 'h', 'Admin', 'ADMIN'))[0][0]

print("Requests, browse, filters")
def mk(title, tags, status='OPEN', hours=1):
    return q(S['INSERT_SEED_REQUEST'], (title, 'a description that is long enough', '{' + ','.join(tags) + '}', status, uid, 'Sam', 0, hours, hours))[0][0]
a = mk('Dark mode', ['ui', 'theme'], 'OPEN', 5); b = mk('Export to CSV', ['data'], 'PLANNED', 4); c = mk('Slack alerts', ['integrations', 'ui'], 'IN_PROGRESS', 3)
d = mk('Offline mode ' + 'x' * 140, [], 'COMPLETED', 2); e = mk('Zero votes request', ['ui'], 'OPEN', 1)
for r, k in ((a, 3), (b, 2), (c, 1)):
    for v in voters[:k]: q(S['CAST_VOTE'], (r, v))
q(S['CAST_VOTE'], (d, voters[0]))
def browse(filters='', args=(), viewer=NIL, order='ORDER_VOTES', page=(20, 0)):
    return q(S['VIEW'] + filters + S[order], (viewer,) + tuple(args) + page)
rows = browse(); cols = ['id', 'title', 'desc', 'tags', 'status', 'author', 'ver', 'cat', 'uat', 'votes', 'mine']
check([r[9] for r in rows] == [3, 2, 1, 1, 0], f"Most Upvoted order {[r[9] for r in rows]}")
check(browse(order='ORDER_NEWEST')[0][1] == 'Zero votes request', "Newest order")
check(len(browse(S['AND_STATUS'], ('PLANNED',))) == 1, "status filter")
check({r[1] for r in browse(S['AND_TAG'], ('ui',))} == {'Dark mode', 'Slack alerts', 'Zero votes request'}, "tag filter")
check(len(browse(S['AND_STATUS'] + S['AND_TAG'], ('OPEN', 'ui'))) == 2, "filters combine")
check(len(browse(S['AND_Q'], ('%csv%', '%csv%'))) == 1 and len(browse(S['AND_Q'], ('%no such%', '%no such%'))) == 0, "keyword search is case-insensitive; empty result is empty")
check(len(browse(S['AND_Q'], ('%100\\%%', '%100\\%%'))) == 0, "a literal % in the keyword is escaped")
check(len(browse(page=(2, 0))) == 2 and len(browse(page=(2, 4))) == 1, "paging")
check(q(S['COUNT_BASE'] + S['AND_TAG'], ('ui',))[0][0] == 3, "count agrees with the filters")
dm = [r for r in browse(viewer=voters[0]) if r[1] == 'Dark mode'][0]
check(dm[10] is True and [r for r in browse() if r[1] == 'Dark mode'][0][10] is False, "viewerHasVoted true for a voter, false for a visitor")
check(len(browse(S['AND_ID'], (d,))[0][3]) == 0 and len(browse(S['AND_ID'], (d,))[0][1]) == 153, "a request with no tags and a 153-char title is stored and read")
check(q(S['TAGS'])[0][0] == 'ui' and q(S['TAGS'])[0][1] == 3, "tags with counts, most used first")

print("RULE 1: one vote per user per request")
def race_votes(n=8):
    res, bar = [], threading.Barrier(n)
    def w():
        cc = conn(); cu = cc.cursor(); bar.wait(); cu.execute(S['CAST_VOTE'], (e, uid)); cc.commit(); cc.close()
    ts = [threading.Thread(target=w) for _ in range(n)]; [t.start() for t in ts]; [t.join() for t in ts]
    return q(S['VOTE_STATE'], (e, e, uid))[0]
st = race_votes(); check(st[0] == 1 and st[1] is True, f"8 simultaneous votes by one user, protection ON: {st[0]} vote")
q(S['WITHDRAW_VOTE'], (e, uid)); q(S['WITHDRAW_VOTE'], (e, uid)); check(q(S['VOTE_STATE'], (e, e, uid))[0] == (0, False), "withdrawing twice is harmless")
q("ALTER TABLE vote DROP CONSTRAINT vote_pkey"); st = race_votes(); check(st[0] == 8, f"same race, protection REMOVED: {st[0]} votes (the bug)")
q("DELETE FROM vote WHERE request_id=%s", (e,)); q("ALTER TABLE vote ADD PRIMARY KEY (request_id, user_id)")

print("RULE 2: two admins, one card (compare and set)")
def race_moves():
    res, bar = [], threading.Barrier(2)
    def w(target):
        cc = conn(); cu = cc.cursor(); bar.wait(); cu.execute(S['MOVE'], (target, a, 0)); res.append(cu.rowcount); cc.commit(); cc.close()
    ts = [threading.Thread(target=w, args=(t,)) for t in ('PLANNED', 'IN_PROGRESS')]; [t.start() for t in ts]; [t.join() for t in ts]; return sorted(res)
check(race_moves() == [0, 1], "two admins with the same version: one wins, one updates nothing (sees STALE_VERSION)")
check(q(S['REQUEST_STATUS'], (a,))[0][1] == 1, "version moved from 0 to 1 exactly once")
q(S['HISTORY_INSERT'], (a, 'OPEN', 'PLANNED', admin, 'Admin (admin)', 'req-1'))
h = q(S['HISTORY_FOR'], (a,)); check(len(h) == 1 and h[0][3] == 'Admin (admin)', "history records who moved what")
q("UPDATE request SET version=0, status='OPEN' WHERE id=%s", (a,))
res = []; bar = threading.Barrier(2)
def naive(t):
    cc = conn(); cu = cc.cursor(); bar.wait(); cu.execute("UPDATE request SET status=%s, version=version+1 WHERE id=%s", (t, a)); res.append(cu.rowcount); cc.commit(); cc.close()
ts = [threading.Thread(target=naive, args=(t,)) for t in ('PLANNED', 'IN_PROGRESS')]; [t.start() for t in ts]; [t.join() for t in ts]
check(sorted(res) == [1, 1], "without the version check both admins believe they won")
check(q(S['REQUEST_STATUS'], (str(uuid.uuid4()),)) == [], "unknown id reads as not found")

print("Roadmap = query over status; seed statements")
check([r[4] for r in browse(S['AND_STATUS'], ('COMPLETED',))] == ['COMPLETED'], "roadmap column is a status query")
q(S['SEED_HISTORY'], (b, 'OPEN', 'PLANNED', admin, 'Demo admin', 10)); check(q("select count(*) from request_status_history")[0][0] >= 2, "seed history inserts")
check(q(S['COUNT_REQUESTS'])[0][0] == 5, "request count")
print(f"\nALL {ok} CHECKS PASSED")
