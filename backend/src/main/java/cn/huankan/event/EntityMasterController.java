package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/entities")public class EntityMasterController {
 private final EntityMasterService service;public EntityMasterController(EntityMasterService s){service=s;}
 public record Query(String search,int page,String kind){} public record Support(UUID relationId,UUID evidenceId,String excerpt){}
 @PostMapping("/search")public Object search(@RequestBody Query q){return service.search(q.search()==null?"":q.search(),q.page());}
 @PostMapping("/{id}")public Object detail(@PathVariable UUID id){return service.detail(id);}
 @PostMapping("/candidates")public Object candidate(@RequestBody EntityMasterService.Candidate c){return Map.of("id",service.candidate(c),"status","待审核");}
 @PostMapping("/relations")public Object relations(@RequestBody Query q){return service.relations(q.kind()==null?"BUSINESS_FACT":q.kind());}
 @PostMapping("/relations/evidence")public Object support(@RequestBody Support q){service.support(q.relationId(),q.evidenceId(),q.excerpt());return Map.of("message","证据已关联，关系保持待审核");}
 public record Review(String decision,String reason,String actor){}
 @PostMapping("/{id}/profiles")public Object profile(@PathVariable UUID id,@RequestBody EntityMasterService.Candidate c){return Map.of("id",service.appendProfile(id,c));}
 @PostMapping("/profiles/{id}/review")public Object review(@PathVariable UUID id,@RequestBody Review r){service.reviewProfile(id,r.decision(),r.reason(),r.actor());return Map.of("message","审核已留痕；历史版本保留");}
 @PostMapping("/{id}/securities")public Object security(@PathVariable UUID id,@RequestBody EntityMasterService.Security s){return Map.of("id",service.security(id,s),"status","待审核候选");}
 @PostMapping("/{id}/identity-review")public Object identity(@PathVariable UUID id,@RequestBody EntityMasterService.Identity i){service.identity(id,i);return Map.of("message","身份消歧依据已保存；未合并或删除历史实体");}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class)public org.springframework.http.ResponseEntity<?> missing(){return org.springframework.http.ResponseEntity.status(404).body(Map.of("message","实体或证据不存在"));}
}
