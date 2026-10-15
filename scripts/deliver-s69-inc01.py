from pathlib import Path
import json,hashlib,datetime,shutil,re
root=Path(__file__).resolve().parents[1];dest=root/'docs/evidence/s69-inc01/20261014T063813Z'
def h(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads(p.read_text(encoding='utf-8-sig'))
session=Path('C:/Users/sthl/.codex/sessions/2026/10/14/rollout-2026-10-14T14-32-09-01a1391c-774f-73f0-91ca-0ca265f91eb9.jsonl')
transcript=[]
for i,s in enumerate(session.read_text(encoding='utf8').splitlines(),1):
 try:r=json.loads(s)
 except Exception:continue
 p=r.get('payload',{})
 if p.get('type') not in ('custom_tool_call','custom_tool_call_output','function_call','function_call_output'):continue
 if p.get('type','').endswith('output'):
  output=p.get('output'); text=output if isinstance(output,str) else '\n'.join(x.get('text','') for x in output or [] if isinstance(x,dict))
 else:text=p.get('input') or p.get('arguments') or ''
 transcript.append({'time':r.get('timestamp'),'sourceLine':i,'type':p.get('type'),'tool':p.get('name'),'callId':p.get('call_id'),'text':text})
(dest/'command-transcript.json').write_text(json.dumps({'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source':str(session),'records':transcript,'scope':'本轮已落盘工具调用/结果；不含推理或加密推理记录，截止交付冻结工具调用之前'},ensure_ascii=False,indent=2),encoding='utf8')
source_files=list((root/'scripts').glob('*s69-inc01*'))+list((root/'docs').glob('S69-INC01-*.md'))+[root/'docs/统一开发状态报告.md']
delivery=dest/'delivery';delivery.mkdir(exist_ok=False)
for p in source_files:
 target=delivery/p.relative_to(root);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
attachments=Path('C:/Users/sthl/.codex/attachments/30d709ab-3601-4874-a204-1f38973eeb39/已粘贴的文本.txt')
shutil.copyfile(attachments,dest/'user-request.txt')
checks=[]
for f in load(dest/'freeze-manifest.json'):
 p=root/f['snapshot'];checks.append({'file':f['snapshot'],'expected':f['sha256'],'actual':h(p)})
prior=load(dest/'artifacts.json')['frozen']
prior_index_changes=[{'file':f['file'],'expected':f['sha256'],'actual':h(Path(f['file']))} for f in prior if h(Path(f['file']))!=f['sha256']]
report={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'frozenSceneFiles':len(checks),'sceneMismatches':[c for c in checks if c['expected']!=c['actual']],'priorIndexChanges':prior_index_changes,'originalRecovery':'No-Go','r3Deployment':'No-Go','frontend5173':'No-Go','new24h':'No-Go','formalRecoveryExecuted':False,'isolationOnly':True,'deliverySources':[str(p.relative_to(root)) for p in source_files]}
(dest/'delivery-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
files=list(dest.rglob('*'));manifest=[{'file':str(p.relative_to(root)),'bytes':p.stat().st_size,'sha256':h(p)} for p in files if p.is_file()]
(dest/'delivery-manifest.json').write_text(json.dumps({'at':report['at'],'files':manifest,'count':len(manifest),'selfExcluded':True},ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(report,ensure_ascii=False,indent=2));print('manifest_files',len(manifest))
