package cn.huankan.reliability;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="huankan.reliability-diagnostics-enabled",havingValue="true",matchIfMissing=true)
public class ObservabilityConfiguration {
    @Bean static BeanPostProcessor measuredDataSources(ObjectProvider<MeterRegistry> registries){
        return new BeanPostProcessor(){@Override public Object postProcessAfterInitialization(Object bean,String name){
            if(bean instanceof HikariDataSource pool)return new ObservedDataSource(pool,registries::getIfAvailable);
            return bean;
        }};
    }
}
