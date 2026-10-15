package cn.huankan.collector;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/gleif")public class GleifController {
 private final GleifService service;public GleifController(GleifService s){service=s;}public record Query(String name,String lei){}
 @PostMapping("/search")public Object search(@RequestBody Query q){return service.search(q.name());}
 @PostMapping("/collect")public Object collect(@RequestBody Query q){return service.collect(q.lei());}
 @PostMapping("/link-relations")public Object link(){return service.linkRelations();}
 @PostMapping("/status")public Object status(){return service.status();}
 @ExceptionHandler({IllegalStateException.class,IllegalArgumentException.class})public org.springframework.http.ResponseEntity<?> failure(RuntimeException e){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message",e.getMessage()));}
}
