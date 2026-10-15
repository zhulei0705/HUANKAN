package cn.huankan.event;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResearchRulesTest {
    @Test void financialTopicsAreReproducibleAndMultiple(){var a=ResearchRules.evaluate("Inflation and monetary policy","","https://official.invalid/a","L0","a");var b=ResearchRules.evaluate("Inflation and monetary policy","","https://official.invalid/a","L0","a");assertEquals(a,b);assertTrue(a.topics().containsAll(java.util.List.of("全球货币政策","通胀")));assertEquals(50,a.score());}
    @Test void sameTitleDifferentUrlsNeverMerge(){assertNotEquals(ResearchRules.evaluate("同名公告","","https://official.invalid/day1","L0","a").clusterKey(),ResearchRules.evaluate("同名公告","","https://official.invalid/day2","L0","b").clusterKey());}
    @Test void articleFragmentsGroupButQueryIdentifiersRemainSeparate(){assertEquals(ResearchRules.canonical("https://official.invalid/a#x"),ResearchRules.canonical("https://official.invalid/a#y"));assertNotEquals(ResearchRules.canonical("https://official.invalid/a?id=1"),ResearchRules.canonical("https://official.invalid/a?id=2"));}
    @Test void missingOriginalUrlKeepsRecordsSeparate(){assertNotEquals(ResearchRules.evaluate("x","","bad","L2","a").clusterKey(),ResearchRules.evaluate("x","","bad","L2","b").clusterKey());}
    @Test void sameSeriesUrlDifferentObservationDatesNeverMerge(){var base=ResearchRules.evaluate("official reference","","https://official.invalid/series","L0","a");assertNotEquals(ResearchRules.observation(base,"EXR.D.USD.EUR.SP00.A:2026-09-01").clusterKey(),ResearchRules.observation(base,"EXR.D.USD.EUR.SP00.A:2026-09-02").clusterKey());assertEquals(ResearchRules.observation(base,"EXR.D.USD.EUR.SP00.A:2026-09-01").clusterKey(),ResearchRules.observation(base,"EXR.D.USD.EUR.SP00.A:2026-09-01").clusterKey());}
    @Test void aggregatedPriorityNeverMeansOfficialFacts(){var a=ResearchRules.evaluate("ECON_TRADE","ECON_TRADE","https://media.invalid/article","L2","a");assertEquals(0,a.basis().get("官方来源"));assertTrue(a.topics().contains("国际贸易"));assertTrue(a.clusterBasis().contains("不代表"));}
    @Test void genericSubstringsDoNotCreateFalseGoldOrGasTopics(){var a=ResearchRules.evaluate("Goldman Vegas daily","","https://media.invalid/a","L2","a");assertFalse(a.topics().contains("贵金属"));assertFalse(a.topics().contains("能源"));}
}
