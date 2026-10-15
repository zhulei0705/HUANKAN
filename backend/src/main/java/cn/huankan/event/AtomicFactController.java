package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/facts")public class AtomicFactController {
 private final AtomicFactService service;public AtomicFactController(AtomicFactService s){service=s;}
 public record Query(int page,String type,int limit){}
 @PostMapping("/extract")public Object extract(@RequestBody Query q){return service.extract(q.limit()==0?500:q.limit());}
 @PostMapping("/query")public Object query(@RequestBody Query q){return service.query(q.page(),q.type()==null?"":q.type());}
 @PostMapping("/{id}/review")public Object review(@PathVariable UUID id,@RequestBody AtomicFactService.Review q){service.review(id,q);return Map.of("message","事实审核已留痕，原始字段与证据未覆盖");}
 @PostMapping("/{id}/history")public Object history(@PathVariable UUID id){return service.history(id);}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
