import pathlib,json,sqlite3,datetime,hashlib
dest=pathlib.Path('docs/evidence/s69-inc01/20261014T063813Z');home=pathlib.Path('C:/Users/sthl/.codex')
result={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'queries':[]}
db=home/'logs_2.sqlite'
try:
 con=sqlite3.connect(db.as_uri()+'?mode=ro',uri=True,timeout=3)
 con.execute('PRAGMA query_only=ON')
 schema=con.execute("SELECT name,sql FROM sqlite_master WHERE type='table'").fetchall();result['schema']=schema
 for name,_ in schema:
  cols=[r[1] for r in con.execute('PRAGMA table_info("'+name+'")')]
  result.setdefault('tables',[]).append({'name':name,'columns':cols})
  if name=='logs' and 'ts' in cols:
   lo=int(datetime.datetime.fromisoformat('2026-10-14T13:30:00+08:00').timestamp());hi=lo+180
   rows=con.execute('SELECT * FROM logs WHERE ts BETWEEN ? AND ? LIMIT 2000',(lo,hi)).fetchall()
   result['queries'].append({'sql':'SELECT * FROM logs WHERE ts BETWEEN ? AND ? LIMIT 2000','params':[lo,hi],'count':len(rows),'rows':[dict(zip(cols,r)) for r in rows]})
 con.close()
except Exception as e:result['error']=str(e)
(dest/'codex-runtime-log.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in result.items() if k!='queries'},ensure_ascii=False,indent=2));print('rows',[(r['count']) for r in result['queries']])
