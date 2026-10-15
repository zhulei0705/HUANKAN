from pathlib import Path
import json,hashlib,datetime,shutil,re
root=Path(__file__).resolve().parents[1];dest=root/'docs/evidence/s69-inc02'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def h(p):return hashlib.sha256(p.read_bytes()).hexdigest()
prior=root/'docs/evidence/s69-inc01/20261014T063813Z/delivery-manifest.json'
checks=[]
for f in read(prior)['files']:
 p=root/f['file'];actual=h(p) if p.exists() else None
 if actual!=f['sha256']:checks.append({'file':f['file'],'expected':f['sha256'],'actual':actual})
# 仅冻结INC02最终源文件/报告，不改INC01/R1—R4。
delivery=dest/'delivery';delivery.mkdir(exist_ok=False)
files=list((root/'scripts').glob('*s69-inc02*'))+[root/'scripts/inc02-process-identity.psm1']+list((root/'scripts/inc02').rglob('*'))+list((root/'docs').glob('S69-INC02-*.md'))+[root/'docs/统一开发状态报告.md']
for p in files:
 if not p.is_file():continue
 q=delivery/p.relative_to(root);q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
link_errors=[]
for p in (root/'docs').glob('S69-INC02-*.md'):
 for t in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf-8-sig')):
  if '://' not in t and not (p.parent/t).exists() and not t.endswith(('delivery-manifest.json','preservation-check.json')):link_errors.append({'file':p.name,'target':t})
result={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'inc01ManifestFilesChecked':len(read(prior)['files']),'priorMismatches':checks,'brokenReportLinks':link_errors,'formalStarted':False,'formalDatabaseWritten':False,'systemTaskRegistered':False,'allFourDecisions':'No-Go'}
(dest/'preservation-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
manifest=[{'file':str(p.relative_to(root)),'bytes':p.stat().st_size,'sha256':h(p)} for p in dest.rglob('*') if p.is_file()]
(dest/'delivery-manifest.json').write_text(json.dumps({'at':result['at'],'files':manifest,'count':len(manifest),'selfExcluded':True},ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(result,ensure_ascii=False,indent=2));print('交付文件数',len(manifest))
if checks or link_errors:raise SystemExit(1)
