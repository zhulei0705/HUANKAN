package cn.huankan.collector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FredConfigurationTest {
    @Test void missingKeyAndWhitelistKeepCollectorDisabled(){var c=new FredConfiguration("","",true);assertFalse(c.ready());assertTrue(c.state().contains("尚未配置"));assertFalse(new FredConfiguration("a".repeat(32),"",true).ready());assertFalse(new FredConfiguration("a".repeat(32),"GDP",false).ready());}
    @Test void validatesWhitelistAndNeverExposesKeyInState(){var c=new FredConfiguration("a".repeat(32),"GDP, CPIAUCSL,GDP",true);assertTrue(c.ready());assertEquals(2,c.whitelist().size());assertFalse(c.state().contains("a".repeat(32)));assertThrows(IllegalArgumentException.class,()->new FredConfiguration("a".repeat(32),"GDP&api_key=bad",true).ready());}
}
