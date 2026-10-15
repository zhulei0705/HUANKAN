from pathlib import Path
import zipfile,json,subprocess,os,sys
root=Path(__file__).resolve().parents[1];dest=root/'docs/evidence/s69-inc02'
jar=root/'backend/target/huankan-s70-reviewed-0.1.0.jar';extract=dest/'original-extracted'
extract.mkdir(exist_ok='--retry' in sys.argv)
with zipfile.ZipFile(jar) as z:
 for n in z.namelist():
  if n.startswith('BOOT-INF/classes/') or n.startswith('BOOT-INF/lib/'):
   target=extract/n
   if n.endswith('/'):target.mkdir(parents=True,exist_ok=True)
   elif not target.exists():target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(n))
javap='C:/Program Files/Java/jdk-21.0.10/bin/javap.exe'
for c in ['cn.huankan.collector.CollectorGuard','cn.huankan.evidence.EvidenceService','cn.huankan.collector.CollectorService']:
 r=subprocess.run([javap,'-c','-p','-classpath',str(extract/'BOOT-INF/classes'),c],capture_output=True)
 if '--retry' not in sys.argv:(dest/(c.split('.')[-1]+'-original-bytecode.txt')).write_bytes(r.stdout+r.stderr)
 if r.returncode:raise RuntimeError('原版字节码读取失败')
env=os.environ.copy();env.update(PGPASSWORD=env['HUANKAN_TEST_DB_PASSWORD'],PGCONNECT_TIMEOUT='5',PGCLIENTENCODING='UTF8')
pg='D:/Program Files/PostgreSQL/18/bin/'
prior=json.loads((root/'runtime/s69-r4-owned-process.json').read_text(encoding='utf-8-sig'))
old=prior['schema'];schema='inc02_'+__import__('uuid').uuid4().hex
if not __import__('re').fullmatch(r'r1_web_[a-f0-9]{32}',old):raise RuntimeError('源隔离schema不匹配')
args=['-h','127.0.0.1','-p','55434','-U',env['HUANKAN_TEST_DB_USER'],'-d','huankan_r4_test','-w']
r=subprocess.run([pg+'pg_dump.exe',*args,'--schema',old,'--no-owner','--no-privileges'],env=env,capture_output=True)
if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
suffix=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--suffix=')),'-r2' if '--retry' in sys.argv else '')
if suffix and not __import__('re').fullmatch(r'-r[0-9]+',suffix):raise RuntimeError('无效隔离轮次')
if not suffix:(dest/'original-isolated-schema.sql').write_bytes(r.stdout)
text=r.stdout.decode('utf8').replace(old,schema)
if suffix:
 fingerprint=json.loads((dest/'fixture-fingerprint.json').read_text(encoding='utf8'))
 placeholder='4'*64
 if text.count(placeholder)!=1 or not __import__('re').fullmatch(r'[0-9a-f]{64}',fingerprint['canonical']):raise RuntimeError('只允许已有R4占位指纹的单点导入准备')
 text=text.replace(placeholder,fingerprint['canonical'])
(dest/('inc02-schema'+suffix+'.sql')).write_text(text,encoding='utf8')
r=subprocess.run([pg+'psql.exe',*args,'-v','ON_ERROR_STOP=1','-f','-'],input=text.encode(),env=env,capture_output=True)
(dest/('schema-clone'+suffix+'.log')).write_bytes(r.stdout+r.stderr)
if r.returncode:raise RuntimeError('隔离schema克隆失败')
(dest/('isolation'+suffix+'.json')).write_text(json.dumps({'port':55434,'database':'huankan_r4_test','schema':schema,'sourceSchema':old,'copiedExistingTestRecordsOnly':True,'canonicalFixtureFingerprintOnImport':bool(suffix),'officialDatabaseTouched':False},ensure_ascii=False,indent=2),encoding='utf8')
print(schema)
