package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/practical/lifecycle") public class EventLifecycleController {
 private final EventLifecycleService s;public EventLifecycleController(EventLifecycleService service){s=service;}
 public record Action(UUID id,String state,String reason){}
 @PostMapping("/query")public Object query(@RequestBody Action q,@RequestHeader("Authorization")String auth){return s.timeline(q.id(),WatchService.owner(auth));}
 @PostMapping("/action")public Object action(@RequestBody Action q,@RequestHeader("Authorization")String auth){return s.action(q.id(),WatchService.owner(auth),q.state(),q.reason());}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.DataAccessException.class})public org.springframework.http.ResponseEntity<?> invalid(){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","事件、工作状态或审计理由无效"));}
}
