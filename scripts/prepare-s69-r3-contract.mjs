import fs from 'node:fs';
const old=JSON.parse(fs.readFileSync('docs/evidence/s69-r2-next-window-contract.json','utf8'));
const contract={...old,version:'S69-R3-1',status:'仅准备验收阈值；未启动窗口',
 start_time:null,finish_time:null,
 additional_admission:{
   transaction_chunks:'候选采集及治理每事务最多50条；治理每轮500条预算不变，原子证据/Outbox提交和租约围栏必须保留',
   normal_load:'记录正常负载与故障注入的独立错误数；有界并发正常阶段连接获取失败必须为0',
   unresolved_risks:'慢I/O来源、正式16万级数据上的治理查询耗时、全部历史持有者及调度排队未完全闭环；部署后用候选关联指标继续核查',
   user_authorizations:['正式Java维护与候选部署','5173静态切换及回滚窗口','独立新24小时窗口启动'],
   deployment_is_not_regression_start:true,
   baseline_failures_immutable:true
 }};
const file='docs/evidence/s69-r3-next-window-contract.json';fs.writeFileSync(file,JSON.stringify(contract,null,2),{flag:'wx'});console.log(file);
