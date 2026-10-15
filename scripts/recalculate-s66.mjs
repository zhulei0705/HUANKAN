import {readFileSync,writeFileSync} from 'node:fs';
const run=JSON.parse(readFileSync('docs/evidence/s66-real-case-api.json','utf8').replace(/^\uFEFF/,''));const c=run.report.calculation;
function decimal(s){const [whole,frac='']=s.split('.');return {n:BigInt(whole+frac),scale:10n**BigInt(frac.length)};}
const a=decimal(c.current_value),b=decimal(c.previous_value),den=a.scale*b.scale,num=a.n*b.scale-b.n*a.scale;
const delta=Number(num)/Number(den);const precision=1000000000000n;const ratioNumerator=num*100n*precision;const ratioDenominator=a.scale*b.n;const rounded=(ratioNumerator+ratioDenominator/2n)/ratioDenominator;
const actual=Number(rounded)/Number(precision);const passed=delta===Number(c.delta)&&actual===Number(c.relative_pct)&&c.current_id===run.report.inputs.observations[0].evidence_id&&c.previous_id===run.report.inputs.observations[1].evidence_id;
writeFileSync('docs/evidence/s66-independent-recalculation.json',JSON.stringify({at:new Date().toISOString(),snapshot:run.id,method:'独立BigInt十进制有理数复算；非调用后端规则',current:c.current_value,previous:c.previous_value,absolute_delta:String(delta),relative_pct:rounded.toString().slice(0,-12)+'.'+rounded.toString().slice(-12),passed,input_evidence_ids:run.report.inputs.observations.map(o=>o.evidence_id)},null,2));if(!passed)throw Error('独立复算失败');console.log('实际数据库快照独立十进制复算通过');
