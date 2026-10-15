package cn.huankan.event;
import java.math.*;import java.time.*;import java.util.*;
/** Decimal calculations over verified literal metadata; no market price inference. */
public final class MacroChangeEngine {
 public static final String RULE="宏观变化-2.0";
 public record Observation(String id,String series,LocalDate period,BigDecimal value,Map<String,String> metadata){}
 private MacroChangeEngine(){}
 public static boolean compatible(Observation a,Observation b){
  if(!a.series.equals(b.series))return false;
  for(String k:List.of("KEY","UNIT","UNIT_MULT","FREQ","CURRENCY","CURRENCY_DENOM","ADJUSTMENT","SEASONAL_ADJUSTMENT","UNIT_INDEX_BASE","COVERAGE"))if(!Objects.equals(a.metadata.get(k),b.metadata.get(k)))return false;
  for(var r:List.of(a,b))if(r.value==null||!r.metadata.getOrDefault("BREAKS","").isBlank()||!Set.of("A","P").contains(r.metadata.getOrDefault("OBS_STATUS","")))return false;
  if(!a.metadata.getOrDefault("CURRENCY_DENOM","").isBlank()&&(a.value.signum()<=0||b.value.signum()<=0))return false;
  return List.of("KEY","UNIT","UNIT_MULT","FREQ").stream().allMatch(k->a.metadata.get(k)!=null&&!a.metadata.get(k).isBlank());
 }
 public static Map<String,Object> calculate(Observation a,Observation b,boolean requireAdjacent){
  var out=new LinkedHashMap<String,Object>();out.put("rule_version",RULE);out.put("calculated_at",Instant.now());out.put("current_id",a.id);out.put("previous_id",b==null?null:b.id);out.put("current_value",a.value==null?null:a.value.toPlainString());out.put("previous_value",b==null||b.value==null?null:b.value.toPlainString());out.put("unit",a.metadata.get("UNIT"));out.put("delta",null);out.put("relative_pct",null);out.put("basis_points",null);out.put("formula","delta=current−previous；relative_pct=(current/previous−1)×100；bp=百分点×100");out.put("precision","十进制原值不舍入；相对变化12位小数，HALF_UP；显示保留计算精度");
  if(b==null){out.put("status","暂无可比数据：未取得前值");return out;}
  if(!compatible(a,b)||!b.period.isBefore(a.period)){out.put("status","暂无可比数据：序列、单位、频率、季调、定义或观测值不一致");return out;}
  String freq=a.metadata.get("FREQ");if(!Set.of("D","B","M","Q","A").contains(freq)){out.put("status","暂无可比数据：频率未配置");return out;}boolean adjacent=switch(freq){case "M"->YearMonth.from(a.period).minusMonths(1).equals(YearMonth.from(b.period));case "Q"->a.period.minusMonths(3).equals(b.period);case "A"->a.period.minusYears(1).equals(b.period);case "D"->a.period.minusDays(1).equals(b.period);case "B"->"E".equals(a.metadata.get("COLLECTION"));default->false;};
  if(requireAdjacent&&!adjacent){out.put("status","暂无可比数据：缺失中间观测或日历不连续，未取得官方营业日日历");return out;}
  BigDecimal delta=a.value.subtract(b.value);out.put("delta",delta.toPlainString());boolean rate=Set.of("PCPA","PC","PCT","百分比","年化百分比","同比增长率","环比增长率").contains(a.metadata.get("UNIT"));out.put("delta_unit",rate?"百分点":a.metadata.get("UNIT"));if(rate)out.put("basis_points",delta.multiply(new BigDecimal("100")).toPlainString());
  boolean relative=!rate&&b.value.signum()>0&&a.value.signum()>=0;
  if(relative)out.put("relative_pct",a.value.subtract(b.value).multiply(new BigDecimal("100")).divide(b.value,12,RoundingMode.HALF_UP).toPlainString());
  out.put("relative_status",relative?"数学相对变化":rate?"百分比或利率水平仅计算百分点及基点":b.value.signum()==0?"分母为零":"非正基期或负值：相对变化缺少经济意义，保留绝对变化");out.put("status","可复算");return out;
 }
 private static boolean complete(List<Observation> rows,Observation a,Observation b){String f=a.metadata.get("FREQ");if(f.equals("B"))return rows.stream().filter(o->!o.period.isBefore(b.period)&&!o.period.isAfter(a.period)).allMatch(o->compatible(a,o));for(LocalDate d=b.period;!d.isAfter(a.period);d=switch(f){case "D"->d.plusDays(1);case "M"->d.plusMonths(1);case "Q"->d.plusMonths(3);case "A"->d.plusYears(1);default->a.period.plusDays(1);}){LocalDate p=d;if(rows.stream().noneMatch(o->o.period.equals(p)&&compatible(a,o)))return false;}return true;}
 public static Map<String,Object> analyze(List<Observation> observations,LocalDate rangeStart){
  var sorted=observations.stream().sorted(Comparator.comparing(Observation::period).reversed()).toList();var out=new LinkedHashMap<String,Object>();if(sorted.isEmpty()){out.put("status","暂无可比数据");return out;}var a=sorted.getFirst();Observation b=sorted.size()>1?sorted.get(1):null;out.put("previous",calculate(a,b,true));String f=a.metadata.get("FREQ");
  for(var entry:Map.of("mom",1,"qoq",3,"yoy",12).entrySet()){boolean applicable=entry.getKey().equals("mom")?f.equals("M"):entry.getKey().equals("qoq")?f.equals("Q"):Set.of("M","Q","A","D").contains(f);LocalDate target=a.period.minusMonths(entry.getValue());var old=sorted.stream().filter(o->o.period.equals(target)).findFirst().orElse(null);var result=calculate(a,applicable?old:null,false);if(!applicable)result.put("status","暂无可比数据：该频率不适用");if(old!=null&&applicable&&!complete(sorted,a,old)){result.put("delta",null);result.put("relative_pct",null);result.put("basis_points",null);result.put("status","暂无可比数据：区间存在缺失观测或口径变化");}out.put(entry.getKey(),result);}
  var start=rangeStart==null?null:sorted.stream().filter(o->o.period.equals(rangeStart)).findFirst().orElse(null);var range=calculate(a,start,false);if(start!=null&&!complete(sorted,a,start)){range.put("delta",null);range.put("relative_pct",null);range.put("basis_points",null);range.put("status","暂无可比数据：区间存在缺失观测或口径变化");}out.put("range",range);return out;
 }
}



