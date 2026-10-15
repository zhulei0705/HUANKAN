package cn.huankan.event;
import java.util.*;import java.math.*;import java.time.*;
/** Compares literal observation definitions, never a fabricated market consensus. */
public final class MacroComparison {
 private MacroComparison(){}
 private static Map<String,Object> fields(Map<String,Object> r){return (Map<String,Object>)r.getOrDefault("source_fields",Map.of());}
 private static LocalDate date(Object v){return v instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(v.toString());}
 public static boolean compatible(Map<String,Object> a,Map<String,Object> b){
  for(String k:List.of("indicator_id","unit","frequency","unit_multiplier","currency","currency_denominator","seasonal_adjustment","base_period","statistical_scope")){
   Object x=fields(a).get(k),y=fields(b).get(k);if(!Objects.equals(x,y))return false;
   if(Set.of("indicator_id","unit","frequency").contains(k)&&(x==null||x.toString().isBlank()))return false;
  }
  for(var r:List.of(a,b)){var f=fields(r);if(Boolean.TRUE.equals(f.get("definition_changed"))||Boolean.TRUE.equals(f.get("missing_value"))||f.get("breaks")!=null&&!f.get("breaks").toString().isBlank())return false;}
  return true;
 }
 public static Map<String,Object> compare(List<Map<String,Object>> inputs){
  if(inputs.isEmpty())throw new IllegalArgumentException("没有真实观测");var a=inputs.getFirst();var out=new LinkedHashMap<String,Object>();
  out.put("current",a);out.put("previous",inputs.size()>1?inputs.get(1):null);for(String k:List.of("difference","percent_change","mom_percent_change","yoy_percent_change","market_expectation","surprise"))out.put(k,null);
  out.put("formula",Map.of("difference","当前值－可比较前值","percent_change","(当前值－可比较前值)÷可比较前值×100%","yoy_percent_change","(当前值－上年同期值)÷上年同期值×100%"));
  out.put("calculation_kind","统计观察；不证明变化原因或市场超预期");out.put("revision_boundary","version仅为本地观测版本，官方完整历史修订未知");
  if(inputs.size()<2){out.put("comparison_status","前期证据不足");return out;}var b=inputs.get(1);
  if(!compatible(a,b)){out.put("comparison_status","指标、单位、频率或统计口径不一致，禁止计算");return out;}
  if(!(a.get("value") instanceof BigDecimal av)||!(b.get("value") instanceof BigDecimal bv)){out.put("comparison_status","存在缺失值，禁止计算");return out;}
  out.put("difference",av.subtract(bv));boolean rate=Set.of("PCPA","PC","PCT","百分比","年化百分比","同比增长率","环比增长率").contains(Objects.toString(fields(a).get("unit"),""));out.put("difference_unit",rate?"百分点":a.get("unit"));out.put("basis_points",rate?av.subtract(bv).multiply(BigDecimal.valueOf(100)):null);
  out.put("percent_change",rate?null:percent(av,bv));LocalDate at=date(a.get("observation_date")),bt=date(b.get("observation_date"));String frequency=Objects.toString(a.get("frequency"),"");
  boolean consecutive=switch(frequency){case "M"->YearMonth.from(at).minusMonths(1).equals(YearMonth.from(bt));case "Q"->at.minusMonths(3).equals(bt);case "A"->at.minusYears(1).equals(bt);case "D"->at.minusDays(1).equals(bt);default->false;};
  if(!consecutive&&!("B".equals(frequency)&&Objects.toString(fields(a).get("indicator_id"),"").startsWith("FM."))){out.put("difference",null);out.put("percent_change",null);out.put("basis_points",null);}
  out.put("consecutive",consecutive);out.put("comparison_status",consecutive?"同口径连续观测":"保存的相邻观测，日历不连续或变更日频率；不声称连续环比");
  if("M".equals(frequency)&&consecutive)out.put("mom_percent_change",rate?null:percent(av,bv));
  for(var old:inputs)if(date(old.get("observation_date")).equals(at.minusYears(1))&&compatible(a,old)&&old.get("value") instanceof BigDecimal ov){out.put("yoy_input",old);out.put("yoy_percent_change",rate?null:percent(av,ov));break;}
  out.put("seasonal_adjustment_note",fields(a).get("seasonal_adjustment")==null?"季调信息未提供；指数数学变化不等同官方季调增长率":"按输入季调口径比较");return out;
 }
 private static BigDecimal percent(BigDecimal a,BigDecimal b){return b.signum()<=0||a.signum()<0?null:a.subtract(b).divide(b,12,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));}
}



