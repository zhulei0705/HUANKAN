package cn.huankan.event;

import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ResearchRules {
    public static final String VERSION="金融规则-1.1";
    private ResearchRules(){}
    private static final Map<String,List<String>> TOPICS;
    static {var rules=new LinkedHashMap<String,List<String>>();
        rules.put("全球货币政策",List.of("monetary","central bank","interest rate","federal reserve","货币政策","利率","央行"));
        rules.put("通胀",List.of("inflation","cpi","ppi","通胀","居民消费价格","工业生产者价格"));
        rules.put("就业",List.of("employment","unemployment","jobs","就业","失业"));
        rules.put("GDP",List.of("gdp","gross domestic product","国内生产总值","国民经济"));
        rules.put("国际贸易",List.of("trade","tariff","export","import","进出口","贸易","关税"));
        rules.put("金融监管",List.of("regulation","regulatory","supervision","监管"));
        rules.put("半导体",List.of("semiconductor","芯片","半导体"));rules.put("人工智能",List.of("artificial intelligence","人工智能"));
        rules.put("算力",List.of("data center","datacentre","computing","算力","数据中心"));rules.put("能源",List.of("energy","oil","gas","能源","石油","天然气"));
        rules.put("贵金属",List.of("gold","silver","precious metal","黄金","白银","贵金属"));rules.put("重大企业公告",List.of("earnings","filing","企业公告","业绩"));
        rules.put("大型资本开支",List.of("capital expenditure","capex","资本开支"));rules.put("供应链变化",List.of("supply chain","供应链"));rules.put("地缘经济风险",List.of("sanction","geopolit","制裁","地缘"));
        rules.put("PMI",List.of("pmi","采购经理"));rules.put("工业增加值",List.of("工业增加值","industrial production"));rules.put("社会消费品零售",List.of("社会消费品零售","retail sales"));rules.put("固定资产投资",List.of("固定资产投资"));rules.put("房地产统计",List.of("房地产"));TOPICS=Collections.unmodifiableMap(rules);
    }
    public record Result(List<String> topics,int score,Map<String,Object> basis,String clusterKey,String clusterBasis,String host){}
    public static Result observation(Result base,String document){return new Result(base.topics(),base.score(),base.basis(),EvidenceService.hash(("observation:"+document).getBytes(StandardCharsets.UTF_8)),"官方序列与观测日期单独成组；同一接口URL不同日期不是重复事件",base.host());}
    public static String canonical(String value){try{URI u=URI.create(value);if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getPort()!=-1)throw new IllegalArgumentException("原文URL异常");return new URI(u.getScheme().toLowerCase(Locale.ROOT),u.getHost().toLowerCase(Locale.ROOT),u.getPath(),u.getQuery(),null).toASCIIString();}catch(Exception e){throw new IllegalArgumentException("原文URL异常",e);}}
    public static Result evaluate(String title,String themes,String url,String level,String evidenceId){
        String input=(title+" "+Objects.toString(themes,"")).toLowerCase(Locale.ROOT);var topics=new ArrayList<String>();var matched=new LinkedHashMap<String,Object>();
        TOPICS.forEach((topic,terms)->{var hits=terms.stream().filter(term->term.chars().allMatch(c->c<128)?java.util.regex.Pattern.compile("(?<![a-z])"+java.util.regex.Pattern.quote(term)+"(?![a-z])").matcher(input).find():input.contains(term)).toList();if(!hits.isEmpty()){topics.add(topic);matched.put(topic,hits);}});
        // GKG topic codes are explicit index labels, never article facts or observed values.
        if(input.contains("econ_")){if(!topics.contains("国际贸易")&&input.contains("econ_trade"))topics.add("国际贸易");if(input.contains("econ_inflation")&&!topics.contains("通胀"))topics.add("通胀");matched.put("聚合主题索引",true);}
        int official="L0".equals(level)?30:0,themeScore=Math.min(40,topics.size()*10),numeric=java.util.regex.Pattern.compile("(?<![a-z])\\d+(?:[.,]\\d+)?(?:%|\\s*(?:billion|million))",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(title).find()?10:0;
        String normalized=null,host=null;try{normalized=canonical(url);host=URI.create(normalized).getHost();}catch(IllegalArgumentException ignored){}
        String basis=normalized==null?"缺少有效原文URL，按证据单独保留":"同一原文URL文章组；不代表同一独立经济事件";
        String key=EvidenceService.hash((normalized==null?"evidence:"+evidenceId:"article:"+normalized).getBytes(StandardCharsets.UTF_8));
        return new Result(List.copyOf(topics),official+themeScore+numeric,Map.of("官方来源",official,"主题命中",themeScore,"标题数值文本",numeric,"命中依据",matched,"规则说明","研究优先级不是真实性或获利概率"),key,basis,host);
    }
}
