package cn.huankan.event;
import java.util.*;import java.time.Instant;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/impact")public class ImpactController{
 private final ImpactService service;public ImpactController(ImpactService s){service=s;}public record Query(UUID factId,Instant asOf,int page){}
 @PostMapping("/analyze")public Object analyze(@RequestBody Query q){return service.analyze(q.factId(),q.asOf());}
 @PostMapping("/query")public Object query(@RequestBody Query q){return service.query(q.page());}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.EmptyResultDataAccessException.class})public org.springframework.http.ResponseEntity<?> failure(){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","截止时刻内的事实证据不足，或查询参数无效"));}
}
