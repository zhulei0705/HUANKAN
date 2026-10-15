import {describe,it,expect} from 'vitest';
import {parseFrame} from '../composables/stream';
import {safeOfficialUrl} from '../api/client';
describe('持久化推送协议',()=>{
 it('解析带消息编号的事件',()=>expect(parseFrame('id: 25\nevent: event\ndata: {"event_id":"fixture-only"}')).toEqual({id:'25',data:{event_id:'fixture-only'}}));
 it('忽略心跳',()=>expect(parseFrame(': 保持连接')).toBeNull());
 it('支持CRLF',()=>expect(parseFrame('id: 1\r\ndata: {}\r\n')?.id).toBe('1'));
});
describe('来源链接安全',()=>{
 it('拒绝脚本和伪造官方域名',()=>{expect(safeOfficialUrl('javascript:alert(1)')).toBe(false);expect(safeOfficialUrl('https://www.sec.gov.evil.invalid/')).toBe(false);expect(safeOfficialUrl('https://evil@www.sec.gov/')).toBe(false);});
 it('允许官方HTTPS来源',()=>{expect(safeOfficialUrl('https://www.sec.gov/Archives/edgar/data/1/test.htm')).toBe(true);expect(safeOfficialUrl('https://fred.stlouisfed.org/series/GDP')).toBe(true);expect(safeOfficialUrl('https://fred.stlouisfed.org.evil.invalid/series/GDP')).toBe(false);});
});
