package cn.huankan.collector;
import java.util.Map;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/macro")public class AlfredController {
 private final AlfredService service;public AlfredController(AlfredService service){this.service=service;}
 @PostMapping("/alfred/status")public Object status(){return service.status();}
 @PostMapping("/alfred/collect")public Object collect(@RequestBody AlfredService.Request q){return service.collect(q);}
 @PostMapping("/unified/query")public Object unified(@RequestBody AlfredService.Query q){return service.unified(q);}
 @ExceptionHandler(IllegalStateException.class)public org.springframework.http.ResponseEntity<?> unavailable(){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message","FRED/ALFRED配置、许可或真实请求未通过；没有生成业务数据"));}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","历史查询参数无效"));}
}
