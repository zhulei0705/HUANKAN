package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/governance")public class GovernanceController {
 private final GovernanceService service;public GovernanceController(GovernanceService s){service=s;}
 public record Batch(int limit,int page){} public record Rule(String id,List<String> terms,int weight,String reason){}
 @PostMapping("/rules")public Object rules(){return service.rules();}
 @PostMapping("/rules/configure")public Object configure(@RequestBody Rule r){return service.configure(r.id(),r.terms(),r.weight(),r.reason());}
 @PostMapping("/process")public Object process(@RequestBody Batch b){return service.process(b.limit());}
 @PostMapping("/cases")public Object cases(@RequestBody Batch b){return service.cases(b.page());}
 @PostMapping("/cases/{id}")public Object detail(@PathVariable UUID id){return service.detail(id);}
 public record Review(String decision,String reason,String actor){}
 @PostMapping("/cases/{id}/facts")public Object fact(@PathVariable UUID id,@RequestBody GovernanceService.Fact f){return Map.of("id",service.fact(id,f));}
 @PostMapping("/facts/{id}/review")public Object review(@PathVariable UUID id,@RequestBody Review r){service.reviewFact(id,r.decision(),r.reason(),r.actor());return Map.of("message","事实审核已记录；不会自动确认整个聚类");}
 @PostMapping("/cases/{id}/hypotheses")public Object hypothesis(@PathVariable UUID id,@RequestBody GovernanceService.Hypothesis h){return Map.of("id",service.hypothesis(id,h));}
 @PostMapping("/cases/{id}/links")public Object link(@PathVariable UUID id,@RequestBody GovernanceService.Link l){return Map.of("id",service.link(id,l),"message","关联保持待审核；未合并原始消息");}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
