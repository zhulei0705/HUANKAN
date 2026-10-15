package cn.huankan.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecConfiguration {
    private final String email, agent, ciks;
    @Value("${huankan.sec-enabled:false}") private boolean enabled;
    public boolean enabled(){return enabled&&hasContact();}
    public SecConfiguration(@Value("${huankan.sec-contact-email}") String email,@Value("${huankan.sec-user-agent}") String agent,@Value("${huankan.sec-ciks}") String ciks) {this.email=email.strip();this.agent=agent.strip();this.ciks=ciks.strip();}
    public boolean hasContact() {return email.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")&&!email.endsWith(".invalid")&&!email.endsWith("example.com")&&!email.contains("\r")&&!email.contains("\n");}
    public String userAgent(){if(!hasContact())throw new IllegalStateException("SEC联系邮箱尚未配置");if(agent.contains("\r")||agent.contains("\n")||agent.length()>200)throw new IllegalStateException("SEC User-Agent无效");return agent.isBlank()?"HUANKAN/0.1 "+email:agent+" "+email;}
    public String followedCiks(){return ciks;}
    public String state(){return state(!ciks.isBlank());}
    public String state(boolean hasFollowed){return !hasContact()?"SEC 联系邮箱尚未配置，自动采集未启用。":!enabled?"SEC_ENABLED未启用，自动采集关闭":!hasFollowed?"待配置关注公司":"已配置，尚需许可核验与连通测试";}
}
