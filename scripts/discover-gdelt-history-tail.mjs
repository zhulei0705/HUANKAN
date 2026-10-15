import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const sql=q=>execFileSync('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-p','5432','-U',process.env.HUANKAN_DB_USER,'-d','huankan','-w','-At','-q','-v','ON_ERROR_STOP=1','-f','-'],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},input:Buffer.from(q),encoding:'utf8'}).trim();
if(process.env.HUANKAN_DB_URL!=='jdbc:postgresql://127.0.0.1:5432/huankan')throw new Error('仅允许寰瞰专用正式库');
if(sql("SELECT s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.id='GDELT';")!=='t')throw new Error('GDELT访问和存储许可未生效');
const gaps=JSON.parse(sql("SELECT COALESCE(json_agg(batch_id),'[]'::json) FROM (SELECT batch_id FROM gdelt_gap WHERE state<>'RESOLVED' ORDER BY batch_id LIMIT 672)t;"));
if(!gaps.length){console.log('没有待检查的历史缺口。');process.exit(0);}
const started=new Date(),url='https://data.gdeltproject.org/gdeltv2/masterfilelist.txt',headers='docs/evidence/s08-history-range-headers.txt';let bytes,status=null,error=null,discovered=[];
try{
 bytes=execFileSync('curl.exe',['-sS','--fail-with-body','--max-time','60','--max-filesize','8388608','-r','-8388608','-D',headers,url],{maxBuffer:9*1024*1024});
 const head=readFileSync(headers,'utf8');status=Number([...head.matchAll(/HTTP\/\S+ (\d{3})/g)].at(-1)?.[1]);
 const range=head.match(/content-range:\s*bytes\s+(\d+)-(\d+)\/(\d+)/i);
 if(status!==206||!range||bytes.length!==Number(range[2])-Number(range[1])+1)throw new Error('服务器未返回完整的有界索引范围，不按部分索引判定缺失');
 writeFileSync('runtime/gdelt-history-tail.txt',bytes);
 const lines=bytes.toString('ascii').split(/\r?\n/);if(Number(range[1])>0)lines.shift();
 const wanted=new Set(gaps);
 for(const line of lines){if(!line.trim().endsWith('.gkg.csv.zip'))continue;const match=line.trim().match(/^(\d+) ([a-f0-9]{32}) https?:\/\/data[.]gdeltproject[.]org\/gdeltv2\/(\d{14})[.]gkg[.]csv[.]zip$/);if(!match)throw new Error('官方索引范围存在无效GKG行');if(!wanted.has(match[3]))continue;const size=Number(match[1]);if(size<1||size>10*1024*1024)throw new Error('正式批次超过本地预算');discovered.push({batch:match[3],size,md5:match[2]});}
 const hash=createHash('sha256').update(bytes).digest('hex');
 const statements=discovered.map(e=>`INSERT INTO gdelt_batch(batch_id,file_url,expected_bytes,expected_md5,status,discovery_kind,availability,retry_at) VALUES('${e.batch}','https://data.gdeltproject.org/gdeltv2/${e.batch}.gkg.csv.zip',${e.size},'${e.md5}','DOWNLOADING','HISTORICAL_BACKFILL','PUBLISHED_NOT_AVAILABLE',now()) ON CONFLICT DO NOTHING; UPDATE gdelt_gap SET state='PUBLISHED_NOT_AVAILABLE',last_checked_at=now() WHERE batch_id='${e.batch}' AND state<>'RESOLVED';`).join('\n');
 sql(`BEGIN; ${statements} INSERT INTO gdelt_index_observation(url,sha256,bytes,records,complete) VALUES('${url}','${hash}',${bytes.length},${discovered.length},true); COMMIT;`);
 writeFileSync('docs/evidence/s08-history-tail-check.json',JSON.stringify({checked_at:new Date().toISOString(),status,content_range:range[0],bytes:bytes.length,sha256:hash,requested_gaps:gaps,index_confirmed:discovered,unconfirmed:gaps.filter(g=>!discovered.some(e=>e.batch===g)),note:'仅官方索引范围中实际出现的记录登记；未出现不等于整份官方索引不存在'},null,2));
 console.log(JSON.stringify({status,bytes:bytes.length,index_confirmed:discovered}));
}catch(e){error=e.code||e.name;throw e;}
finally{const escaped=url.replaceAll("'","''");sql(`INSERT INTO http_request_observation(source_id,endpoint,attempted_at,completed_at,status,duration_ms,response_bytes,error,failure_kind) VALUES('GDELT','${escaped}','${started.toISOString()}',now(),${status||'NULL'},${Date.now()-started.getTime()},${bytes?.length||0},${error?"'CONTROLLED_INDEX_RANGE_FAILURE'":'NULL'},${error?"'INDEX_FAILURE'":'NULL'});`);}
