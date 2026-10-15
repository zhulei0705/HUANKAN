package cn.huankan.collector;
import java.util.Map;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/macro/history")public class MacroHistoryController {
 private final MacroHistoryService service;public MacroHistoryController(MacroHistoryService service){this.service=service;}
 @PostMapping("/collect")public Object collect(@RequestBody MacroHistoryService.Request q){return service.collect(q);}
 @PostMapping("/query")public Object query(@RequestBody MacroHistoryService.Query q){return service.query(q);}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(IllegalStateException.class)public org.springframework.http.ResponseEntity<?> unavailable(){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message","历史采集未成功或正在执行，请查看实际请求状态"));}
}
