package cn.huankan.event;
import org.junit.jupiter.api.Test;import java.util.*;import java.time.*;import java.math.*;import static org.junit.jupiter.api.Assertions.*;
class MacroChangeEngineTest {
 MacroChangeEngine.Observation row(String date,String value,String freq,String unit){var m=new HashMap<String,String>(Map.of("KEY","ISOLATED","UNIT",unit,"UNIT_MULT","0","FREQ",freq,"OBS_STATUS","A"));return new MacroChangeEngine.Observation(date,"ISOLATED",LocalDate.parse(date),value==null?null:new BigDecimal(value),m);}
 Map<String,Object> calc(MacroChangeEngine.Observation a,MacroChangeEngine.Observation b){return MacroChangeEngine.calculate(a,b,true);}
 @Test void ratesUsePercentagePoints(){var r=calc(row("2026-10-02","3.75","D","PCPA"),row("2026-10-01","3.5","D","PCPA"));assertEquals("0.25",r.get("delta"));assertEquals("百分点",r.get("delta_unit"));assertNull(r.get("relative_pct"));}
 @Test void basisPoints(){assertEquals("25.00",calc(row("2026-10-02","3.75","D","PCPA"),row("2026-10-01","3.5","D","PCPA")).get("basis_points"));}
 @Test void zeroDenominator(){assertNull(calc(row("2026-10-02","1","D","USD"),row("2026-10-01","0","D","USD")).get("relative_pct"));}
 @Test void negativeInterestAllowed(){assertEquals("0.25",calc(row("2026-10-02","-0.25","D","PCPA"),row("2026-10-01","-0.50","D","PCPA")).get("delta"));}
 @Test void negativeQuantityRetainsDelta(){var r=calc(row("2026-10-02","-2","D","EUR"),row("2026-10-01","-3","D","EUR"));assertEquals("1",r.get("delta"));assertNull(r.get("relative_pct"));}
 @Test void originalPrecision(){assertEquals("1.123456789012345",calc(row("2026-10-02","1.123456789012345","D","USD"),row("2026-10-01","1","D","USD")).get("current_value"));}
 @Test void dailyGap(){assertNull(calc(row("2026-10-05","2","D","USD"),row("2026-10-02","1","D","USD")).get("delta"));}
 @Test void missingValue(){assertNull(calc(row("2026-10-02",null,"D","USD"),row("2026-10-01","1","D","USD")).get("delta"));}
 @Test void oneObservation(){assertNull(calc(row("2026-10-02","1","D","USD"),null).get("delta"));}
 @Test void mismatchedDefinitions(){for(String key:List.of("KEY","UNIT","UNIT_MULT","FREQ","ADJUSTMENT","SEASONAL_ADJUSTMENT","CURRENCY_DENOM","UNIT_INDEX_BASE")){var a=row("2026-10-02","2","D","USD");var b=row("2026-10-01","1","D","USD");b.metadata().put(key,"DIFFERENT");assertNull(calc(a,b).get("delta"),key);}}
 @Test void seriesMismatch(){var a=row("2026-10-02","2","D","USD");var b=row("2026-10-01","1","D","USD");assertFalse(MacroChangeEngine.compatible(a,new MacroChangeEngine.Observation(b.id(),"OTHER",b.period(),b.value(),b.metadata())));}
 @Test void definitionBreak(){var a=row("2026-10-02","2","D","USD");a.metadata().put("BREAKS","break");assertNull(calc(a,row("2026-10-01","1","D","USD")).get("delta"));}
 @Test void quoteDirection(){var a=row("2026-10-02","2","D","USD");var b=row("2026-10-01","1","D","USD");a.metadata().put("CURRENCY_DENOM","EUR");b.metadata().put("CURRENCY_DENOM","JPY");assertNull(calc(a,b).get("delta"));}
 @Test void periodSorting(){var r=MacroChangeEngine.analyze(List.of(row("2026-09-01","1","M","INDEX"),row("2026-10-01","2","M","INDEX")),null);assertEquals("2026-10-01",((Map<?,?>)r.get("previous")).get("current_id"));}
 @Test void monthlyComparison(){var r=MacroChangeEngine.analyze(List.of(row("2026-10-01","110","M","INDEX"),row("2026-09-01","100","M","INDEX")),null);assertEquals("100",((Map<?,?>)r.get("mom")).get("previous_value"));}
 @Test void quarterlyComparison(){var r=MacroChangeEngine.analyze(List.of(row("2026-10-01","110","Q","INDEX"),row("2026-07-01","100","Q","INDEX")),null);assertEquals("10",((Map<?,?>)r.get("qoq")).get("delta"));}
 @Test void yearlyComparison(){var r=MacroChangeEngine.analyze(List.of(row("2026-10-01","110","A","INDEX"),row("2025-10-01","100","A","INDEX")),null);assertEquals("10",((Map<?,?>)r.get("yoy")).get("delta"));}
 @Test void rangeComparison(){var r=MacroChangeEngine.analyze(List.of(row("2026-10-02","2","D","USD"),row("2026-10-01","1","D","USD")),LocalDate.parse("2026-10-01"));assertEquals("1",((Map<?,?>)r.get("range")).get("delta"));}
 @Test void eventChangeFrequency(){var a=row("2026-10-02","3.75","B","PCPA");a.metadata().put("COLLECTION","E");var b=row("2026-07-01","3.5","B","PCPA");b.metadata().put("COLLECTION","E");assertEquals("0.25",calc(a,b).get("delta"));}
 @Test void noRowsEmpty(){assertEquals("暂无可比数据",MacroChangeEngine.analyze(List.of(),null).get("status"));}
}
