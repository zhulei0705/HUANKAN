package cn.huankan.event;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/research")
public class ResearchController {
    private final ResearchService service;public ResearchController(ResearchService service){this.service=service;}
    public record Query(int page,String topic,String series,boolean officialOnly){} public record Process(boolean reprocess,int limit){} public record Link(UUID eventId,UUID evidenceId,String reason){}
    public record Relation(UUID subjectId,UUID objectId,String type,UUID evidenceId,String explanation,Instant effectiveFrom,Instant effectiveUntil){}
    public record Review(String target,UUID id,String decision,UUID evidenceId,String reason,String quote){}
    @PostMapping("/candidates")public Object candidates(@RequestBody Query q){return service.candidates(q.page(),q.topic()==null?"":q.topic(),q.officialOnly());}
    @PostMapping("/process")public Object process(@RequestBody Process q){return service.process(q.reprocess(),q.limit()==0?500:q.limit());}
    @PostMapping("/events/{id}")public Object detail(@PathVariable UUID id){return service.detail(id);}
    @PostMapping("/proof/{id}")public Object proof(@PathVariable UUID id){return service.proof(id);}
    @PostMapping("/entities")public Object entities(@RequestBody Query q){return service.entities(q.page());}
    @PostMapping("/relations")public Object relations(){return service.relations();}
    @PostMapping("/macro")public Object macro(@RequestBody Query q){return service.macro(q.series()==null?"":q.series(),q.page());}
    @PostMapping("/official-evidence")public Object link(@RequestBody Link q){service.link(q.eventId(),q.evidenceId(),q.reason());return Map.of("message","官方证据已关联，事件事实仍待审核");}
    @PostMapping("/relations/create")public Object relation(@RequestBody Relation q){return Map.of("id",service.relation(q.subjectId(),q.objectId(),q.type(),q.evidenceId(),q.explanation(),q.effectiveFrom(),q.effectiveUntil()));}
    @PostMapping("/review")public Object review(@RequestBody Review q){service.review(q.target(),q.id(),q.decision(),q.evidenceId(),q.reason(),q.quote());return Map.of("message","审核结论和证据依据已记录");}
    @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class)public org.springframework.http.ResponseEntity<?> unavailable(IllegalStateException e){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class)public org.springframework.http.ResponseEntity<?> missing(){return org.springframework.http.ResponseEntity.status(404).body(Map.of("message","请求的事件、实体或证据不存在"));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)public org.springframework.http.ResponseEntity<?> constraint(){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message","关联对象无效或未通过证据与数据约束校验"));}
}
