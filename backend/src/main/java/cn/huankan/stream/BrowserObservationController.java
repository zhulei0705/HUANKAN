package cn.huankan.stream;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/stream/browser") public class BrowserObservationController {
 private final BrowserObservationService service;public BrowserObservationController(BrowserObservationService service){this.service=service;}
 @PostMapping("/ack") public Object ack(@RequestBody List<BrowserObservationService.Receipt> r){return service.acknowledge(r);}
 @PostMapping("/clock") public Object clock(){return service.clock();}
 @PostMapping("/status") public Object status(){return service.status();}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
