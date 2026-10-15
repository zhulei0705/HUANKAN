import json,hashlib,zipfile, pathlib,datetime
root=pathlib.Path(__file__).resolve().parents[1]; dest=root/'docs/evidence/s69-inc01/20261014T063813Z'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
jars=[];entries=[]
for name in ['huankan-s70-reviewed-0.1.0.jar','huankan-s69-r3-candidate-0.1.0.jar']:
 p=root/'backend/target'/name
 with zipfile.ZipFile(p) as z:
  m={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')};entries.append(m)
  jars.append(dict(path=str(p),sha256=digest(p),bytes=p.stat().st_size,manifest=z.read('META-INF/MANIFEST.MF').decode(),application=z.read('BOOT-INF/classes/application.yml').decode(),migrationCount=sum(n.startswith('BOOT-INF/classes/db/migration/') and n.endswith('.sql') for n in m)))
checks=[]
for name in ['s69-r1-final-index-20261013T115021.json','s69-r1-delivery-20261013T120843.json','s69-r2-delivery-20261014T014238.json','s69-r3-delivery-20261014T041942.json','s69-r4-delivery-20261014T060115.json']:
 p=root/'docs/evidence'/name; data=read(p); mismatches=[]
 for f in data['manifest']:
  snap=root/f['snapshot']; actual=digest(snap) if snap.exists() else None
  if actual!=f['sha256'].lower():mismatches.append(dict(file=f['snapshot'],expected=f['sha256'],actual=actual))
 checks.append(dict(file=str(p),sha256=digest(p),count=len(data['manifest']),mismatches=mismatches))
a,b=entries
diff=dict(added=[n for n in b if n not in a],removed=[n for n in a if n not in b],changed=[n for n in a if n in b and a[n]!=b[n]])
result=dict(at=datetime.datetime.now(datetime.timezone.utc).isoformat(),jars=jars,diff=diff,migrationChanges=[n for n in set(a)|set(b) if n.startswith('BOOT-INF/classes/db/migration/') and a.get(n)!=b.get(n)],frozen=checks)
(dest/'artifacts.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(dict(jars=[{k:v for k,v in j.items() if k not in ['application','manifest']} for j in jars],diff={k:len(v) for k,v in diff.items()},migrationChanges=result['migrationChanges'],frozen=[{k:v for k,v in c.items() if k!='sha256'} for c in checks]),ensure_ascii=False,indent=2))
