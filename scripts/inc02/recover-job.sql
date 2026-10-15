-- 由隔离测试器在显式事务内调用。本轮禁止在5432执行。
-- 调用者以set_config设置huankan.inc02.job_id和expected_job，search_path必须精确指定。
DO $inc02$
DECLARE
 j ingestion_job%ROWTYPE;
 l collector_lease%ROWTYPE;
 before_job jsonb;
 after_job jsonb;
 cursor_before jsonb;
 nraw bigint;
 nout bigint;
 audit_key text;
BEGIN
 SELECT * INTO STRICT j FROM ingestion_job WHERE id=current_setting('huankan.inc02.job_id')::uuid FOR UPDATE;
 audit_key='INC02_RECOVER_'||j.id::text;
 IF j.status='FAILED' AND j.error_code='OWNER_LOST' AND EXISTS(
   SELECT 1 FROM audit_log WHERE action=audit_key AND (target::jsonb)->'after'=to_jsonb(j)) THEN
   RETURN; -- 同一终态与审计完全一致：幂等，不重写完成时间或重复审计。
 END IF;
 IF to_jsonb(j)<>current_setting('huankan.inc02.expected_job')::jsonb THEN RAISE EXCEPTION '任务现场已变化，拒绝恢复'; END IF;
 IF j.status<>'RUNNING' OR j.finished_at IS NOT NULL THEN RAISE EXCEPTION '任务并非未结束RUNNING'; END IF;
 IF NOT pg_try_advisory_xact_lock(742020,hashtext(j.source_id)) THEN RAISE EXCEPTION '来源锁仍由活动采集器持有'; END IF;
 SELECT * INTO l FROM collector_lease WHERE source_id=j.source_id FOR UPDATE;
 IF NOT FOUND OR l.owner IS DISTINCT FROM j.lease_owner THEN RAISE EXCEPTION '租约归属缺失或变化'; END IF;
 IF l.expires_at>=clock_timestamp() THEN RAISE EXCEPTION '租约尚未过期'; END IF;
 IF j.heartbeat_at IS NOT NULL AND j.heartbeat_at>=clock_timestamp()-interval '2 minutes' THEN RAISE EXCEPTION '任务心跳仍新鲜'; END IF;
 SELECT to_jsonb(c) INTO cursor_before FROM source_cursor c WHERE source_id=j.source_id FOR UPDATE;
 SELECT count(*) INTO nraw FROM raw_evidence WHERE job_id=j.id;
 SELECT count(*) INTO nout FROM stream_outbox o JOIN raw_evidence r ON r.id=o.evidence_id WHERE r.job_id=j.id;
 IF nraw<>nout OR EXISTS(SELECT 1 FROM raw_evidence r WHERE r.job_id=j.id AND NOT EXISTS(
   SELECT 1 FROM stream_outbox o JOIN financial_event e ON e.id=o.event_id WHERE o.evidence_id=r.id AND e.evidence_id=r.id))
 THEN RAISE EXCEPTION '证据、事件和Outbox引用不一致，需人工核查'; END IF;
 before_job=to_jsonb(j);
 UPDATE ingestion_job SET status='FAILED',finished_at=clock_timestamp(),error_code='OWNER_LOST',
   error_detail='INC02：进程中断；保留既有计数与证据；等待幂等重试'
 WHERE id=j.id RETURNING to_jsonb(ingestion_job.*) INTO after_job;
 INSERT INTO audit_log(actor,action,target) VALUES('HUANKAN-S69-INC02',audit_key,
   jsonb_build_object('before',before_job,'after',after_job,'lease_before',to_jsonb(l),
    'cursor_before',cursor_before,'committed_raw',nraw,'committed_outbox',nout,
    'classification',CASE WHEN nraw=0 THEN 'NO_COMMITTED_EVIDENCE' ELSE 'COMMITTED_UNFINISHED_TASK' END,
    'note','没有证据不等于已证明HTTP未完成或事务回滚；保留旧计数，不推进游标，不删除租约，不创建业务事件')::text);
END
$inc02$;
