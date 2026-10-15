export async function post<T>(path:string,token:string,body:unknown={}):Promise<T> {
 const response=await fetch('/api'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body)});
 if(!response.ok) throw new Error(response.status===401?'访问密钥无效':`请求失败（${response.status}），请查看服务运行状态`);
 return response.json();
}
export function safeOfficialUrl(url:string):boolean {
 try {const u=new URL(url);return u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&['www.federalreserve.gov','www.sec.gov','www.ecb.europa.eu','www.stats.gov.cn','data-api.ecb.europa.eu','data.gdeltproject.org','fred.stlouisfed.org','api.gleif.org','www.gleif.org','api.bls.gov','www.bls.gov','data.bls.gov'].includes(u.hostname);}catch{return false;}
}
export function safeArticleUrl(url:string):boolean {
 try{const u=new URL(url);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password&&!u.port;}catch{return false;}
}
