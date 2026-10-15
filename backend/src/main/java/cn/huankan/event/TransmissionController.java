package cn.huankan.event;
import java.util.*;import java.time.*;import org.springframework.web.bind.annotation.*;import java.security.Principal;
@RestController @RequestMapping("/api/transmission") public class TransmissionController {
 private final TransmissionService service;public TransmissionController(TransmissionService service){this.service=service;}
 public record Request(UUID evidenceId,UUID id,UUID eventId,Instant asOf,int page){}
 @PostMapping("/input") public Object input(@RequestBody Request r){return service.input(r.eventId());}
 @PostMapping("/catalog") public Object catalog(){return service.catalog();}
 @PostMapping("/analyze") public Object analyze(@RequestBody Request r,@RequestHeader("Authorization") String authorization){return service.analyze(WatchService.owner(authorization),r.evidenceId(),r.asOf());}
 @PostMapping("/query") public Object query(@RequestBody Request r,@RequestHeader("Authorization") String authorization){return service.query(WatchService.owner(authorization),r.id(),r.page());}
 @PostMapping("/reproduce") public Object reproduce(@RequestBody Request r,@RequestHeader("Authorization") String authorization){return service.reproduce(WatchService.owner(authorization),r.id());}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.EmptyResultDataAccessException.class}) public org.springframework.http.ResponseEntity<?> invalid(){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message","真实观测、可比计算或当前身份研究证据不足，请核对输入与截止时刻"));}
}
