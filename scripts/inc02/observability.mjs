// 可复用的告警判定；不启动观察循环，不修改数据库或进程。
export function evaluate({identityValid,health,pool,logTail,exitCode,ecbState,logWriting}){
 const alerts=[];
 if(!identityValid)alerts.push('PROCESS_IDENTITY_MISMATCH');
 if(health!=='UP')alerts.push('HEALTH_UNAVAILABLE');
 if(!pool)alerts.push('POOL_PROBE_UNAVAILABLE');
 else {if(pool.ThreadsAwaitingConnection>0)alerts.push('JDBC_PENDING');if(pool.ActiveConnections>=pool.TotalConnections&&pool.IdleConnections===0&&pool.TotalConnections>0)alerts.push('POOL_FULL');}
 if(/Connection is not available|SQLTransientConnectionException|SCHEDULER_DATABASE_UNAVAILABLE|SQLSTATE\(08/.test(logTail||''))alerts.push('JDBC_FAILURE');
 if(exitCode!==null&&exitCode!==undefined)alerts.push(exitCode===0?'PROCESS_STOPPED':'PROCESS_EXIT_FAILURE');
 if(ecbState==='STALE_RUNNING')alerts.push('ECB_TASK_STALE');
 if(!logWriting)alerts.push('LOG_NOT_WRITING');
 return {alerts,action:alerts.length?'暂停验收、保存现场、人工核查；仅经完整身份和授权后正常关闭，不自动重启':'本次采样未触发告警；不能证明长期稳定'};
}
