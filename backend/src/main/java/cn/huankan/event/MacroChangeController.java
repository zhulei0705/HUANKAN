package cn.huankan.event;
import java.time.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/macro-changes") public class MacroChangeController {
 private final MacroChangeService service;public MacroChangeController(MacroChangeService s){service=s;}
 public record Query(String series,int page,String source,LocalDate from,LocalDate to,Instant asOf,LocalDate rangeStart){}
 @PostMapping("/catalog") public Object catalog(@RequestBody Query q){return service.catalog(q.page(),q.source());}
 @PostMapping("/overview") public Object overview(){return service.overview();}
 @PostMapping("/detail") public Object detail(@RequestBody Query q){return service.detail(q.series(),q.page(),q.from(),q.to(),q.asOf(),q.rangeStart());}
 @ExceptionHandler(IllegalArgumentException.class) public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
