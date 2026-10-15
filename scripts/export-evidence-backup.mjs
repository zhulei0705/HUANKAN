import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import {writeFileSync,mkdirSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {createHash} from 'node:crypto';
const directory=resolve(process.env.HUANKAN_BACKUP_DIRECTORY||'');
if(!directory.startsWith(resolve('runtime/backups')+ '\\'))throw new Error('证据导出仅允许项目备份目录');
const query="SELECT json_build_object('id',id,'raw_content_hash',raw_content_hash,'body',encode(raw_content,'base64')) FROM raw_evidence ORDER BY id;";
const exportProcess=spawn('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-p','5432','-U',process.env.HUANKAN_DB_USER,'-d','huankan','-w','-At','-q','-v','ON_ERROR_STOP=1','-c',query],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_DB_PASSWORD},stdio:['ignore','pipe','pipe']});
let failure='';exportProcess.stderr.on('data',chunk=>{failure=(failure+chunk.toString()).slice(0,4000)});
const completed=new Promise((resolve,reject)=>{exportProcess.on('error',reject);exportProcess.on('close',code=>code===0?resolve():reject(new Error('证据导出数据库读取失败：'+failure)));});
mkdirSync(join(directory,'evidence'));
const manifest=[];
const checksum=createHash('sha256');
try{for await(const line of createInterface({input:exportProcess.stdout,crlfDelay:Infinity})){if(!line)continue;if(line.length>32*1024*1024)throw new Error('单份证据超出导出上限');const row=JSON.parse(line);if(!/^[a-f0-9-]{36}$/.test(row.id))throw new Error('证据ID无效');const bytes=Buffer.from(row.body,'base64'),hash=createHash('sha256').update(bytes).digest('hex');if(hash!==row.raw_content_hash)throw new Error('备份证据哈希失败');writeFileSync(join(directory,'evidence',row.id+'.bin'),bytes);if(manifest.length)checksum.update(',');checksum.update(row.id+hash);manifest.push({id:row.id,sha256:hash,bytes:bytes.length});}await completed;}catch(error){exportProcess.kill();await completed.catch(()=>{});throw error;}
writeFileSync(join(directory,'evidence-manifest.json'),JSON.stringify({captured_at:new Date().toISOString(),count:manifest.length,manifest:checksum.digest('hex'),evidence:manifest},null,2));
console.log('已备份并逐字节校验 '+manifest.length+' 份真实原始证据。');
