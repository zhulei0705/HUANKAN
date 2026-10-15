package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;import cn.huankan.collector.*;
@RestController @RequestMapping("/api/practical") public class PracticalIntelligenceController {
 private final PracticalIntelligenceService service;private final OfficialResourceService resources;private final BlsFileCollector files;private final SecCompanyService companies;private final SecConfiguration sec;private final MacroResearchService macro;
 public PracticalIntelligenceController(PracticalIntelligenceService s,OfficialResourceService r,BlsFileCollector f,SecCompanyService c,SecConfiguration cfg,MacroResearchService m){service=s;resources=r;files=f;companies=c;sec=cfg;macro=m;}
 public record Id(UUID id){}public record Series(String series,int page){}public record Years(int first,int last){}
 @PostMapping("/catalog")public Object catalog(){return service.catalog();}@PostMapping("/monitor")public Object monitor(){return service.monitor();}
 @PostMapping("/cards")public Object cards(@RequestBody PracticalIntelligenceService.Query q,@RequestHeader("Authorization")String auth){return service.cards(q,WatchService.owner(auth));}
 @PostMapping("/card/detail")public Object detail(@RequestBody Id q){return service.detail(q.id());}
 @PostMapping("/macro")public Object macro(@RequestBody Series q){return service.macro(q.series(),q.page());}
 @PostMapping("/macro/analyze")public Object analyze(@RequestBody MacroResearchController.Analyze q){return macro.analyze(q.series(),q.asOf());}
 @PostMapping("/brief/generate")public Object generate(@RequestBody PracticalIntelligenceService.Query q,@RequestHeader("Authorization")String auth){return service.generate(q,WatchService.owner(auth));}
 @PostMapping("/brief/query")public Object query(@RequestBody PracticalIntelligenceService.Query q,@RequestHeader("Authorization")String auth){return service.briefs(q,WatchService.owner(auth));}
 @PostMapping("/brief/detail")public Object brief(@RequestBody Id q,@RequestHeader("Authorization")String auth){return service.brief(q.id(),WatchService.owner(auth));}
 @PostMapping("/resource/status")public Object resources(){return resources.status();}@PostMapping("/resource/diagnose")public Object diagnose(){return resources.diagnose();}@PostMapping("/calendar/sync")public Object calendar(){return resources.syncCalendars();}@PostMapping("/bls-file/sync")public Object files(@RequestBody Years q){return files.sync(q.first(),q.last());}
 @PostMapping("/sec/preflight")public Object sec(){return Map.of("configuration",companies.status(),"contact_configured",sec.hasContact(),"automatic_enabled",sec.enabled(),"forms",List.of("8-K","10-Q","10-K","8-K/A","10-Q/A","10-K/A"),"xbrl_model","按CIK、taxonomy、concept、单位、会计期间、accession和版本留存；无配置不采集","endpoint","https://data.sec.gov/api/xbrl/companyfacts/CIK##########.json","limit","复用SEC专用统一预算；本项目保守2次/秒，低于官方10次/秒","state","数据模型准备不等于正式披露接入；未请求SEC Key或密码");}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,org.springframework.dao.DataAccessException.class})public org.springframework.http.ResponseEntity<?> invalid(){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","真实输入、许可、身份或日期未通过校验；请核对本地配置与运行记录"));}
}
