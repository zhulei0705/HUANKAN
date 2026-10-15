package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/practical/priority") public class PriorityController {
 private final PriorityService service;public PriorityController(PriorityService s){service=s;}
 public record Snapshot(UUID id,int page){}
 @PostMapping("/overview") public Object overview(@RequestHeader("Authorization")String a){return service.overview(WatchService.owner(a));}
 @PostMapping("/rank") public Object rank(@RequestBody PriorityService.Query q,@RequestHeader("Authorization")String a){return service.rank(q,WatchService.owner(a));}
 @PostMapping("/snapshot") public Object snapshot(@RequestBody Snapshot q,@RequestHeader("Authorization")String a){return service.snapshot(q.id(),WatchService.owner(a),q.page());}
 @PostMapping("/reproduce") public Object reproduce(@RequestBody Snapshot q,@RequestHeader("Authorization")String a){return service.reproduce(q.id(),WatchService.owner(a));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.DataAccessException.class}) public org.springframework.http.ResponseEntity<?> invalid(){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","排序条件、身份或快照无效"));}
}
