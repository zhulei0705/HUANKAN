package cn.huankan.event;

import java.time.*;
import java.util.*;

/** Sorting hypotheses never change verification or notification qualification. */
public final class PriorityRules {
 public static final String VERSION="重要事件研究排序-1.1";
 public static final String LEGACY_VERSION="重要事件研究排序-1.0";
 public static final String RULE_ID="OFFICIAL_EVENT_RESEARCH_PRIORITY";
 private PriorityRules(){}
 public record Input(String eventId,String evidenceId,String level,String publisher,Instant publishedAt,
   Instant asOf,boolean intact,String factStatus,List<String> categories,boolean watchMatch,
   boolean comparableChange,boolean verifiedRelation,boolean revision,String ingestionKind){
  public Input(String eventId,String evidenceId,String level,String publisher,Instant publishedAt,Instant asOf,boolean intact,String factStatus,List<String> categories,boolean watchMatch,boolean comparableChange,boolean verifiedRelation,boolean revision){this(eventId,evidenceId,level,publisher,publishedAt,asOf,intact,factStatus,categories,watchMatch,comparableChange,verifiedRelation,revision,null);}
 }
 public record Score(int total,Map<String,Integer> components,List<String> reasons){}
 public static Score score(Input i){
  return score(i,false);
 }
 public static Score score(Input i,String version){
  if(!VERSION.equals(version)&&!LEGACY_VERSION.equals(version))throw new IllegalArgumentException("历史排序规则版本未加载");
  return score(i,LEGACY_VERSION.equals(version));
 }
 private static Score score(Input i,boolean legacy){
  var p=new LinkedHashMap<String,Integer>();var why=new ArrayList<String>();
  boolean official="L0".equals(i.level()),usable=i.intact()&&!Set.of("CONFLICT","REJECTED").contains(i.factStatus());
  p.put("官方来源等级",official&&usable?25:0);
  long age=i.publishedAt()==null?Long.MAX_VALUE:Duration.between(i.publishedAt(),i.asOf()).toSeconds();
  boolean freshAllowed=legacy||official&&usable&&"NEW_EVENT".equals(i.ingestionKind());
  p.put("官方发布时间新近程度",freshAllowed?(age>=0&&age<=86400?15:age>86400&&age<=604800?8:0):0);
  p.put("证据完整且字段核对",official&&usable&&Set.of("FIELD_MATCHED","INDEPENDENT_SUPPORTED").contains(i.factStatus())?15:0);
  p.put("发布机构身份",official&&usable&&!i.publisher().isBlank()?5:0);
  p.put("金融类别命中",i.categories().isEmpty()?0:5);
  p.put("核心宏观指标类别",i.categories().stream().anyMatch(Set.of("货币政策","通货膨胀","就业","GDP","汇率")::contains)?10:0);
  p.put("个人关注匹配",i.watchMatch()?10:0);
  p.put("同口径实际数值变化",official&&usable&&i.comparableChange()?10:0);
  p.put("已核实实体关系",official&&usable&&i.verifiedRelation()?3:0);
  p.put("新证据修订",usable&&i.revision()?2:0);
  if(!legacy&&!usable)p.replaceAll((k,v)->0);
  p.forEach((k,v)->why.add(k+"："+v+"分"));
  if(!official)why.add("聚合线索保持待核验；数量与重复提及不加分");
  if(!usable)why.add("证据损坏或事实冲突，不进入可信事实排序");
  if(i.publishedAt()==null)why.add("官方发布时间未知，采集时间不能替代发布新近程度");
  if(!legacy&&!"NEW_EVENT".equals(i.ingestionKind()))why.add("历史回填、来源修订或采集性质未知，不计真实新发布加分；仍保留证据研究价值");
  why.add("研究优先级用于安排核查，不代表市场影响强度、预测准确率或获利概率");
  return new Score(p.values().stream().mapToInt(Integer::intValue).sum(),p,List.copyOf(why));
 }
 public static List<String> categories(String title,String series){
  String t=Objects.toString(title,"").toLowerCase(Locale.ROOT);var out=new ArrayList<String>();
  String[][] rules={{"货币政策","monetary","interest rate","货币政策","利率"},{"通货膨胀","inflation","cpi","ppi","消费价格","生产者价格"},{"就业","employment","unemployment","就业","失业"},{"GDP","gdp","gross domestic product","国内生产总值"},{"汇率","exchange rate","汇率"},{"国际贸易","trade","tariff","贸易","进出口"},{"金融监管","regulat","supervision","监管"},{"重大企业披露","earnings","filing","财报","业绩"},{"AI与半导体","artificial intelligence","semiconductor","人工智能","半导体","芯片"},{"光通信与数据中心","optical","data center","光通信","数据中心"},{"能源与大宗商品","energy","crude oil","能源","大宗商品","石油"},{"产业链重大变化","supply chain","供应链","产业链"}};
  for(String[] r:rules)for(int n=1;n<r.length;n++)if(t.contains(r[n])){out.add(r[0]);break;}
  if(series!=null){String c=series.startsWith("EXR.")?"汇率":series.startsWith("FM.")?"货币政策":null;if(c!=null&&!out.contains(c))out.add(c);}
  return List.copyOf(out);
 }
}
