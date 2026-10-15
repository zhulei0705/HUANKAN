import {writeFileSync} from 'node:fs';import {execFileSync} from 'node:child_process';
const sql=q=>execFileSync('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-p','5432','-U',process.env.HUANKAN_DB_USER,'-d','huankan','-w','-Atq','-v','ON_ERROR_STOP=1','-f','-'],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},input:q,encoding:'utf8'}).trim();
const calls=[];async function post(path,body={}){const start=performance.now();const r=await fetch('http://127.0.0.1:18080/api/'+path,{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:JSON.stringify(body)});let result;try{result=await r.json();}catch{result={error:'响应不是JSON'};}calls.push({path,request:body,http:r.status,result,duration_ms:performance.now()-start,checked_at:new Date().toISOString()});if(!r.ok)throw Error(path+': HTTP '+r.status);return result;}
try{
sql(`UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=clock_timestamp(),review_basis='2026-10-10实查GLEIF官方LEI Data Terms of Use第二章第3项CC0；仅保存公开法人登记参考数据和会计并表/报告例外JSON，未获得证券行情等第三方许可' WHERE source_id='GLEIF' AND status='PENDING';`);
for(let i=0;i<4;i++){const r=await post('facts/extract',{limit:500});if(!r.macro_inserted&&!r.publication_inserted)break;}
await post('gleif/collect',{lei:'HWUPKR0MPOU8FGXBT394'});await post('gleif/collect',{lei:'HWUPKR0MPOU8FGXBT394'});
await post('gleif/collect',{lei:'5493004SYPRAVRVNK561'});await post('gleif/link-relations');await post('facts/extract',{limit:500});
const facts=await post('facts/query',{page:0,type:'MACRO_OBSERVATION'});for(const f of facts.slice(0,3))await post('impact/analyze',{factId:f.id,asOf:new Date().toISOString()});
await post('ai/status');await post('gleif/status');
}finally{writeFileSync('docs/evidence/s38-real-api-exercises.json',JSON.stringify({started_phase:'S31-S38',finished_at:new Date().toISOString(),calls},null,2));console.log(JSON.stringify(calls.map(c=>({path:c.path,http:c.http,result:c.result,failed:c.failed,reason:c.reason})),null,2));}
