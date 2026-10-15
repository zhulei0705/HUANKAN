package cn.huankan.event;

import java.math.BigDecimal;
import java.util.*;

/** Conditional economic mechanisms, never confirmed company or market effects. */
public final class TransmissionRules {
 public static final String VERSION="经济传导机制-1.0";
 private TransmissionRules(){}
 public record Template(String id,String name,String variable,String output,List<String> conditions,List<String> evidence,List<String> contradictions){}
 public static final List<Template> TEMPLATES=List.of(
  new Template("FX_REVENUE","汇率与外币收入折算","FX","报表收入折算",List.of("真实外币收入金额、币种和报表本位币","适用会计期间与折算汇率","成本和对冲安排"),List.of("企业原始收入披露","会计政策","对冲披露"),List.of("收入与成本自然对冲","金融对冲抵销","实际结算期间或会计折算汇率不同")),
  new Template("FX_IMPORT","汇率与进口成本","FX","本币进口成本",List.of("真实进口采购敞口与计价币种","合同重定价时间","成本转嫁和对冲安排"),List.of("采购合同或原始成本披露","计价与结算条款"),List.of("供应商调整外币售价","长期锁价或对冲抵销","进口原料用量变化")),
  new Template("FX_EXPORT","汇率与出口价格竞争力","FX","出口需求条件",List.of("真实出口市场和计价币种","竞争者价格和需求弹性","售价调整能力"),List.of("出口区域披露","合同币种","销量与售价证据"),List.of("差异化产品需求稳定","合同以买方本币锁价","需求或竞争价格变化抵销")),
  new Template("RATE_FINANCE","政策利率与融资成本","RATE","债务重定价成本",List.of("真实浮动利率债务敞口","重定价期限与基准挂钩","信用利差与对冲安排"),List.of("债务原始披露","利率合同","对冲披露"),List.of("固定利率债务未重定价","信用利差反向变化","利率对冲抵销")),
  new Template("RATE_DISCOUNT","政策利率与估值折现假设","RATE","现金流折现假设",List.of("合法估值输入及现金流","风险溢价和折现率构成","政策利率向折现率传导证据"),List.of("现金流披露","合法估值观测"),List.of("现金流预期同步改变","风险溢价反向变化","政策变化已被市场预期")),
  new Template("INFLATION_COST","通胀与企业成本压力","INFLATION","经营成本",List.of("可比通胀分项","企业投入成本结构","成本转嫁能力"),List.of("官方分项观测","企业成本披露"),List.of("收入售价同步提高","采购锁价","分项不匹配企业投入")),
  new Template("GROWTH_DEMAND","经济增长与周期性需求","GROWTH","周期需求",List.of("真实增长口径和区域","企业业务区域与周期敞口"),List.of("官方增长观测","区域收入披露"),List.of("结构性增长未覆盖该业务","库存或订单周期不同")),
  new Template("COMMODITY_COST","商品价格与原材料成本","COMMODITY","原料成本",List.of("合法真实商品价格","原材料采购敞口及合同"),List.of("合法商品观测","企业采购披露"),List.of("锁价与对冲","替代原料","售价转嫁")));
 public static String variable(String series,Map<String,String> metadata){
  if(series.startsWith("EXR.")&&Objects.equals(metadata.get("KEY"),series)&&metadata.get("CURRENCY_DENOM")!=null&&!metadata.get("CURRENCY_DENOM").isBlank()&&"0".equals(metadata.get("UNIT_MULT"))&&Objects.equals(metadata.get("UNIT"),metadata.get("CURRENCY")))return "FX";
  if(series.startsWith("FM.")&&Objects.equals(metadata.get("KEY"),series)&&"PCPA".equals(metadata.get("UNIT")))return "RATE";
  return "UNKNOWN";
 }
 public static String direction(String variable,Map<String,String> metadata,BigDecimal delta){
  if(variable.equals("FX")){String base=metadata.get("CURRENCY_DENOM"),quote=metadata.get("UNIT");return delta.signum()>0?base+"相对于"+quote+"升值；"+quote+"相对于"+base+"贬值":delta.signum()<0?base+"相对于"+quote+"贬值；"+quote+"相对于"+base+"升值":"官方参考汇率观测值未变";}
  return variable.equals("RATE")?(delta.signum()>0?"政策利率水平上升":delta.signum()<0?"政策利率水平下降":"政策利率水平未变"):"变量定义尚未配置，不能解释方向";
 }
 public static List<Map<String,Object>> candidates(String variable,Map<String,String> metadata,BigDecimal delta){
  var out=new ArrayList<Map<String,Object>>();for(var t:TEMPLATES)if(t.variable.equals(variable)){
   var m=new LinkedHashMap<String,Object>();m.put("mechanism_id",t.id);m.put("mechanism_name_zh",t.name);m.put("mechanism_type","条件性经济机制");m.put("input_variable_type",variable);m.put("output_variable_type",t.output);m.put("applicable_conditions",t.conditions);m.put("required_evidence_types",t.evidence);m.put("assumptions",List.of("其他条件不变只是假设，需要实际证据核验","官方参考值不是企业实际结算价格"));m.put("transmission_direction",direction(variable,metadata,delta));m.put("limitations",List.of("机制不是本事件中已证实的因果关系","不生成受益股票、利润变动或市场涨跌"));m.put("contradiction_conditions",t.contradictions);m.put("mechanism_version",VERSION);m.put("review_status","规则代码与测试审核；经济因果未验证");m.put("status","机制适用条件部分满足");m.put("hypothesis_status","假设待验证");m.put("matched_basis","已核验的同口径官方变量与数学变化匹配机制输入");m.put("known_conditions",List.of("真实官方变量与单位","两期原始证据和可复算变化"));m.put("unknown_conditions",t.conditions);m.put("counter_evidence",List.of());m.put("counter_evidence_status","未取得真实反向证据；反面解释不是已发生证据");
   String hypothesis=switch(t.id){case "FX_REVENUE"->"若报表本位币为"+metadata.get("CURRENCY_DENOM")+"且存在"+metadata.get("UNIT")+"收入，固定外币金额按该参考值倒数折算的数学方向与报价变动相反；实际收入影响仍需金额、期间、成本与对冲证据";case "FX_IMPORT"->"若进口成本以"+metadata.get("UNIT")+"计价、本位币为"+metadata.get("CURRENCY_DENOM")+"，固定外币金额的参考折算成本方向与报价变动相反；实际结算和成本转嫁未知";case "FX_EXPORT"->"若出口以卖方本币定价，报价变化可能改变海外买家的外币价格；需求与竞争力方向取决于计价、价格调整和需求弹性";default->"若真实企业敞口、期限与合同挂钩成立，政策利率变化可能传导至"+t.output+"；实际幅度与市场结果尚未验证";};m.put("hypothesis",hypothesis);out.add(m);
  }return out;
 }
}
