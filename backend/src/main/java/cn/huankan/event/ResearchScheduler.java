package cn.huankan.event;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class ResearchScheduler {
    private final ResearchService service;private final GovernanceService governance;private final boolean enabled;
    public ResearchScheduler(ResearchService service,GovernanceService governance,@org.springframework.beans.factory.annotation.Value("${huankan.collector-enabled}") boolean enabled){this.service=service;this.governance=governance;this.enabled=enabled;}
    @Scheduled(fixedDelay=30000,initialDelay=20000)public void run(){if(enabled)try{
        // Keep the 500-row work budget, releasing transactions and locks every 50 rows.
        for(int batch=0;batch<10;batch++){
            int research=((Number)service.process(false,50).get("processed")).intValue();
            int governed=((Number)((java.util.Map<?,?>)governance.process(50)).get("processed")).intValue();
            if(research<50&&governed<50)break;
        }
    }catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("事件治理批次失败，保留待处理记录",e);}}
}
