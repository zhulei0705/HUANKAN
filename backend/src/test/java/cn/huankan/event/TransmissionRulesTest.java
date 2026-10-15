package cn.huankan.event;
import org.junit.jupiter.api.Test;import java.util.*;import java.math.*;import java.time.*;import static org.junit.jupiter.api.Assertions.*;
class TransmissionRulesTest {
 final String series="EXR.D.USD.EUR.SP00.A";
 Map<String,String> metadata(){return Map.of("KEY",series,"UNIT","USD","CURRENCY","USD","CURRENCY_DENOM","EUR","UNIT_MULT","0","FREQ","D","OBS_STATUS","A");}
 @Test void quoteDirectionUsesVerifiedDenominator(){assertEquals("FX",TransmissionRules.variable(series,metadata()));assertEquals("EUR相对于USD升值；USD相对于EUR贬值",TransmissionRules.direction("FX",metadata(),new BigDecimal("0.0020")));}
 @Test void decreaseReversesDirection(){assertEquals("EUR相对于USD贬值；USD相对于EUR升值",TransmissionRules.direction("FX",metadata(),new BigDecimal("-0.0020")));}
 @Test void noChangeDoesNotClaimAppreciation(){assertEquals("官方参考汇率观测值未变",TransmissionRules.direction("FX",metadata(),BigDecimal.ZERO));}
 @Test void titleCannotCreateVariable(){assertEquals("UNKNOWN",TransmissionRules.variable("CPI",metadata()));assertEquals("UNKNOWN",TransmissionRules.variable(series,Map.of()));}
 @Test void exactMathPreservesInputs(){var a=new MacroChangeEngine.Observation("a",series,LocalDate.parse("2026-10-09"),new BigDecimal("1.1206"),metadata());var b=new MacroChangeEngine.Observation("b",series,LocalDate.parse("2026-10-08"),new BigDecimal("1.1186"),metadata());var c=MacroChangeEngine.calculate(a,b,true);assertEquals("0.0020",c.get("delta"));assertEquals("0.178794922224",c.get("relative_pct"));assertEquals("a",c.get("current_id"));assertEquals("b",c.get("previous_id"));}
 @Test void referenceFxIsConditional(){var r=TransmissionRules.candidates("FX",metadata(),BigDecimal.ONE);assertEquals(3,r.size());r.forEach(m->{assertEquals("机制适用条件部分满足",m.get("status"));assertEquals("假设待验证",m.get("hypothesis_status"));assertFalse(((List<?>)m.get("unknown_conditions")).isEmpty());assertTrue(((List<?>)m.get("counter_evidence")).isEmpty());assertEquals(TransmissionRules.VERSION,m.get("mechanism_version"));});}
 @Test void revenueRequiresExposureAndInverseConversion(){var r=TransmissionRules.candidates("FX",metadata(),BigDecimal.ONE).getFirst();assertTrue(r.get("hypothesis").toString().contains("倒数"));assertTrue(r.get("hypothesis").toString().contains("对冲"));}
 @Test void unknownVariablesRunNoMechanism(){assertTrue(TransmissionRules.candidates("UNKNOWN",Map.of(),BigDecimal.ONE).isEmpty());}
 @Test void ratesDoNotBecomeFx(){assertEquals("RATE",TransmissionRules.variable("FM.B.U2.EUR.4F.KR.DFR.LEV",Map.of("KEY","FM.B.U2.EUR.4F.KR.DFR.LEV","UNIT","PCPA")));assertEquals(2,TransmissionRules.candidates("RATE",Map.of(),new BigDecimal("0.25")).size());}
 @Test void allTemplatesHaveEvidenceAndContradictions(){assertEquals(8,TransmissionRules.TEMPLATES.size());for(var t:TransmissionRules.TEMPLATES){assertFalse(t.conditions().isEmpty());assertFalse(t.evidence().isEmpty());assertFalse(t.contradictions().isEmpty());}}
 @Test void deterministicCandidates(){assertEquals(TransmissionRules.candidates("FX",metadata(),BigDecimal.ONE),TransmissionRules.candidates("FX",metadata(),BigDecimal.ONE));}
}
