package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ai")public class AiResearchController {
 private final AiResearchService service;public AiResearchController(AiResearchService s){service=s;}
 public record Event(UUID eventId){}
 @PostMapping("/status")public Object status(){return service.status();}
 @PostMapping("/history")public Object history(@RequestBody Event e){return service.history(e.eventId());}
 @PostMapping("/analyze")public Object analyze(@RequestBody Event e){return service.analyze(e.eventId());}
 @ExceptionHandler(IllegalStateException.class)public org.springframework.http.ResponseEntity<?> unavailable(IllegalStateException e){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class)public org.springframework.http.ResponseEntity<?> missing(){return org.springframework.http.ResponseEntity.status(404).body(Map.of("message","事件证据不足或许可尚未确认"));}
}
