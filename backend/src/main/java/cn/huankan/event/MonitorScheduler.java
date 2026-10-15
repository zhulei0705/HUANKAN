package cn.huankan.event;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="huankan.collector-enabled",havingValue="true")
public class MonitorScheduler {
    private final QueryService query;public MonitorScheduler(QueryService query){this.query=query;}
    @Scheduled(fixedDelay=30000,initialDelay=30000) public void check(){query.alerts();}
}
