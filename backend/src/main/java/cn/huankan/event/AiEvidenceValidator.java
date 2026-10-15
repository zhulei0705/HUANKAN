package cn.huankan.event;
import java.util.*;import com.fasterxml.jackson.databind.JsonNode;
public final class AiEvidenceValidator {
 private AiEvidenceValidator(){}
 public record Validation(boolean passed,List<String> errors){}
 public static Validation validate(JsonNode output,Map<String,String> evidence){var errors=new ArrayList<String>();
  if(!output.isObject()||output.has("tool_calls")||output.has("function_call"))return new Validation(false,List.of("模型输出不是允许的研究对象"));
  for(String field:List.of("extracted_facts","hypotheses","uncertainties","counter_evidence"))if(!output.path(field).isArray()||output.path(field).size()>100)errors.add("结构字段无效："+field);
  if(!output.path("uncertainties").isArray()||output.path("uncertainties").isEmpty())errors.add("缺少不确定性说明");
  for(var fact:output.path("extracted_facts")){String id=fact.path("evidence_id").asText(),quote=fact.path("quote").asText(),claim=fact.path("claim").asText();String source=evidence.get(id);if(source==null||quote.length()<5||quote.length()>4000||!source.contains(quote)||claim.isBlank()){errors.add("事实缺少真实摘录或证据引用");continue;}
   var pattern=java.util.regex.Pattern.compile("(?<![a-zA-Z0-9])[-+]?\\d+(?:[.,]\\d+)*(?:\\s*%)?");var sourceNumbers=new HashSet<String>();var quoted=pattern.matcher(quote);while(quoted.find())sourceNumbers.add(quoted.group().replaceAll("\\s+",""));var numbers=pattern.matcher(claim);while(numbers.find())if(!sourceNumbers.contains(numbers.group().replaceAll("\\s+","")))errors.add("事实数字与摘录不一致");
   var financialUnits=java.util.regex.Pattern.compile("(?i)USD|EUR|CNY|JPY|GBP|billion|million|percent|%|亿元|万元|同比|环比").matcher(claim);while(financialUnits.find()){if(!quote.toLowerCase(Locale.ROOT).contains(financialUnits.group().toLowerCase(Locale.ROOT))||!fact.hasNonNull("value")||!fact.hasNonNull("unit"))errors.add("金融数字单位或比较口径缺少完整来源检查");}
   if(fact.hasNonNull("value")&&!quote.contains(fact.get("value").asText()))errors.add("结构化金融数值缺少对应原文");
   for(String field:List.of("unit","currency","observation_date","published_date"))if(fact.hasNonNull(field)&&!fact.path(field).asText().isBlank()&&!quote.contains(fact.path(field).asText()))errors.add("金融字段与引用不一致："+field);
   if(fact.hasNonNull("unit")&&!FinancialUnitGuard.consistent(fact.path("unit").asText(),quote))errors.add("百分比、百分点、基点或金额单位不一致");
   for(String identifier:List.of("entity_name","company_identifier","indicator_id"))if(fact.hasNonNull(identifier)&&!quote.contains(fact.path(identifier).asText()))errors.add("公司或指标标识不在实际引用中");
   String kind=fact.path("value_kind").asText();if(Set.of("FORECAST","YOY","MOM","REVISION").contains(kind)){String lower=quote.toLowerCase(Locale.ROOT);boolean supported=switch(kind){case "FORECAST"->lower.contains("forecast")||quote.contains("预测")||quote.contains("预计");case "YOY"->lower.contains("year-on-year")||quote.contains("同比");case "MOM"->lower.contains("month-on-month")||quote.contains("环比");default->lower.contains("revis")||quote.contains("修订");};if(!supported)errors.add("预测、同比、环比或修订属性证据不足");}
  }
  for(var hypothesis:output.path("hypotheses")){if(hypothesis.path("mechanism").asText().isBlank()||!hypothesis.path("evidence_ids").isArray()||hypothesis.path("evidence_ids").isEmpty())errors.add("影响假设没有证据引用");for(var id:hypothesis.path("evidence_ids"))if(!evidence.containsKey(id.asText()))errors.add("影响假设引用未知证据");}
  var supportedNumbers=new HashSet<String>();var numeric=java.util.regex.Pattern.compile("(?<![a-zA-Z0-9])[-+]?\\d+(?:[.,]\\d+)*(?:\\s*%)?");
  for(var fact:output.path("extracted_facts")){if(evidence.containsKey(fact.path("evidence_id").asText())&&evidence.get(fact.path("evidence_id").asText()).contains(fact.path("quote").asText())){var m=numeric.matcher(fact.path("quote").asText());while(m.find())supportedNumbers.add(m.group().replaceAll("\\s+",""));}}
  for(String field:List.of("translated_title","summary")){String value=output.path(field).asText();if(value.length()>8000)errors.add("模型文本过长");var m=numeric.matcher(value);while(m.find())if(!supportedNumbers.contains(m.group().replaceAll("\\s+","")))errors.add("标题或摘要包含未通过事实引用的数字");}
  return new Validation(errors.isEmpty(),List.copyOf(errors));
 }
}
