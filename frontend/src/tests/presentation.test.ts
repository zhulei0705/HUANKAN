import {describe,it,expect} from 'vitest';import {title,indicator,sourceName,http,duration,time,utc,health,institutions} from '../presentation/zh';
describe('中文金融展示语义',()=>{
it('登记机构全部有中文名称',()=>{expect(Object.keys(institutions)).toHaveLength(9);for(const id of Object.keys(institutions))expect(sourceName(id)).toMatch(/[\u3400-\u9fff]/);});
it('未执行翻译不使用未核验中文字段',()=>{expect(title({original_title:'Federal Reserve',chinese_title:'无记录译文',translation_status:'NOT_CONFIGURED'}).text).toBe('中文翻译待完成');expect(title({chinese_title:'译文',translation_status:'SUCCESS'}).text).toBe('中文翻译待完成');});
it('人工核验和实际执行机器译文分别标注',()=>{expect(title({chinese_title:'核验',translation_status:'HUMAN_VERIFIED'}).status).toBe('人工核验译文');expect(title({chinese_title:'译文',translation_status:'SUCCESS',translation_execution_id:'execution'}).status).toBe('机器翻译');});
it('币种与报价方向明确，仅映射注册完整指标',()=>{expect(indicator('EXR.D.CNY.EUR.SP00.A')).toContain('1欧元对应人民币');expect(indicator('EXR.D.UNKNOWN.EUR.SP00.A')).toBeUndefined();expect(title({series_id:'EXR.D.JPY.EUR.SP00.A'}).text).toContain('日元');});
it('HTTP与待配置状态不混淆',()=>{expect(http(304)).toContain('内容未修改');expect(http(403)).toContain('访问被拒绝');expect(http(429)).toContain('频率受限');expect(health({id:'BLS',http_status:403}).key).toBe('failed');expect(health({id:'SEC',enabled:false,license_status:'PENDING'}).key).toBe('pending');});
it('北京时间与原始UTC一致，不把未知覆盖为当前时间',()=>{expect(time('2026-10-10T00:00:00Z')).toContain('08:00:00');expect(utc('2026-10-10T08:00:00+08:00')).toBe('2026-10-10T00:00:00.000Z');expect(time(null,'官方发布时间未知')).toBe('官方发布时间未知');});
it('秒数转为中文时长，保留未知与异常',()=>{expect(duration(58793)).toBe('约16小时20分钟');expect(duration(null)).toBe('未知');expect(duration(-1)).toContain('异常');});
it('HTTP错误不能被历史成功任务覆盖，304保持成功语义',()=>{expect(health({id:'BLS',http_status:'403',job_status:'SUCCESS',enabled:true,license_status:'APPROVED'}).key).toBe('failed');expect(health({id:'FED',http_status:503,job_status:'SUCCESS',enabled:true,license_status:'APPROVED'}).text).toContain('官方服务异常');expect(health({id:'FED',http_status:304,job_status:'SUCCESS',enabled:true,license_status:'APPROVED'}).key).toBe('normal');});
});
