export function continuityResult(report){
 const samples=report.samples||[],start=Date.parse(report.started_at),end=Date.parse(report.finished_at),gaps=[];
 for(let i=1;i<samples.length;i++){const gap=Date.parse(samples[i].at)-Date.parse(samples[i-1].at);if(gap>90000||gap<=0)gaps.push({at:samples[i].at,milliseconds:gap});}
 const edgeCovered=samples.length>1&&Date.parse(samples[0].at)-start<=90000&&end-Date.parse(samples.at(-1).at)<=90000;
 const errors=samples.filter(s=>s.error||s.health_http!==200||s.health?.status!=='UP'||s.sse?.http!==200||!s.database||Date.parse(s.process?.created_at)!==Date.parse(report.backend?.createdAt));
 const complete=Number.isFinite(start)&&Number.isFinite(end)&&end-start>=86400000&&edgeCovered&&gaps.length===0&&errors.length===0&&!(report.interruptions||[]).length;
 const max=key=>Math.max(0,...samples.map(s=>Number(key(s))||0));
 return {status:complete?'连续24小时运行采样核验通过':'连续运行证据不足或存在中断',start:report.started_at,end:report.finished_at,sample_count:samples.length,edge_covered:edgeCovered,gaps,errors:errors.map(s=>({at:s.at,error:s.error||'健康、数据库、SSE状态或进程身份异常'})),interruptions:report.interruptions||[],max_working_set:max(s=>s.process?.working_set),max_cpu_percent:max(s=>s.process?.cpu_percent),max_connections:max(s=>s.database?.connections),max_audit_backlog:max(s=>s.database?.audit_backlog),source_failures:'来源访问失败单独列示；此结论不代表全部来源可用或真实LIVE通过'};
}
export function browserResult(report){const samples=report.samples||[];return {start:report.started_at,end:report.ended_at,complete:report.complete===true,sample_count:samples.length,errors:samples.filter(s=>s.error||s.pageStates?.length!==2||s.pageStates.some(x=>!x.includes('已连接'))).length,two_client_samples:Math.max(0,...samples.map(s=>s.two_client_samples||0)),status:samples.some(s=>s.two_client_samples>0)?'存在双客户端样本，需逐条核查在线接收、渲染、ACK及事件资格':'等待真实官方LIVE样本',limitations:'页面连接文字与历史状态采样不能证明每个事件的在线交付；不等同两个独立浏览器引擎验收'};}
