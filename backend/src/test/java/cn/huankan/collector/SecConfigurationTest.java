package cn.huankan.collector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SecConfigurationTest {
 @Test void blankContactNeverEnablesSec(){var config=new SecConfiguration("","HUANKAN","");assertFalse(config.hasContact());assertThrows(IllegalStateException.class,config::userAgent);}
 @Test void rejectsPlaceholderContact(){assertFalse(new SecConfiguration("fake@example.com","","").hasContact());}
 @Test void identifiesAConfiguredAgentWithoutMakingNetworkRequests(){var config=new SecConfiguration("test-fixture@local.test","HUANKAN","");assertEquals("HUANKAN test-fixture@local.test",config.userAgent());}
 @Test void persistedFollowedCompaniesSatisfyPreparationWithoutEnvironmentCiks(){var config=new SecConfiguration("test-fixture@local.test","HUANKAN","");org.springframework.test.util.ReflectionTestUtils.setField(config,"enabled",true);assertEquals("待配置关注公司",config.state(false));assertEquals("已配置，尚需许可核验与连通测试",config.state(true));}
}
