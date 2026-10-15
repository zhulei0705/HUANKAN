import {writeFileSync} from 'node:fs';
const batches=[];
for(let i=0;i<100;i++){const r=await fetch('http://127.0.0.1:18080/api/research/process',{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:JSON.stringify({limit:500,reprocess:false}),signal:AbortSignal.timeout(90000)});if(!r.ok)throw new Error('治理 HTTP '+r.status);const result=await r.json();batches.push({...result,time:new Date().toISOString()});if(!result.processed)break;await new Promise(resolve=>setTimeout(resolve,250));}
writeFileSync('docs/evidence/s18-processing.json',JSON.stringify({checked_at:new Date().toISOString(),batches},null,2));console.log(JSON.stringify({batches:batches.length,processed:batches.reduce((a,b)=>a+b.processed,0)}));
