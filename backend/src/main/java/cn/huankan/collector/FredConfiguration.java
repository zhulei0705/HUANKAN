package cn.huankan.collector;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class FredConfiguration {
    private final String key,series;private final boolean enabled;
    public FredConfiguration(@Value("${FRED_API_KEY:}") String key,@Value("${FRED_SERIES_WHITELIST:}") String series,@Value("${FRED_ENABLED:false}") boolean enabled){this.key=key;this.series=series;this.enabled=enabled;}
    public boolean keyConfigured(){return key.matches("[a-fA-F0-9]{32}");}
    public List<String> whitelist(){if(series.isBlank())return List.of();var list=Arrays.stream(series.split(",")).map(String::strip).distinct().toList();if(list.size()>20||list.stream().anyMatch(s->!s.matches("[A-Za-z0-9_]{1,40}")))throw new IllegalArgumentException("FRED序列白名单无效或超过20项");return list;}
    String key(){return key;}
    public boolean ready(){return enabled&&keyConfigured()&&!whitelist().isEmpty();}
    public String state(){return !keyConfigured()?"FRED API Key尚未配置，自动采集未启用。":whitelist().isEmpty()?"FRED经济序列白名单尚未配置。":!enabled?"FRED自动采集已关闭。":"配置具备，仍需许可核验及真实接口验证。";}
}
