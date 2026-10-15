import {it,expect} from 'vitest';import {querySql} from '../../uiProjection';
it('只读查询在数据库分页并以北京日期边界过滤',()=>{const sql=querySql({page:2,start:'2026-10-09',end:'2026-10-09',dimension:'published_at'},'owner');expect(sql).toContain('BEGIN READ ONLY');expect(sql).toContain('LIMIT 50 OFFSET 100');expect(sql).toContain("AT TIME ZONE 'Asia/Shanghai'");expect(sql).toContain('count(*)');});
it('动态查询拒绝非法字段、页码和日期',()=>{for(const value of [{dimension:'bad_column'},{page:-1},{start:'2026-02-30'},{source:"FED'"},{start:'2026-10-11',end:'2026-10-10'}])expect(()=>querySql(value,'owner')).toThrow();});
it('输入中的引号作为字符串内容处理',()=>{expect(querySql({category:"类型'字符"},'owner')).toContain("类型''字符");});
