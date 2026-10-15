package cn.huankan.event;
import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/watch/updates")public class WatchUpdateController {
 private final WatchUpdateService s;public WatchUpdateController(WatchUpdateService service){s=service;}public record Cursor(long before,long id){}
 @PostMapping("/query")public Object query(@RequestHeader("Authorization")String auth,@RequestBody Cursor q){return s.query(WatchService.owner(auth),q.before());}
 @PostMapping("/read")public Object read(@RequestHeader("Authorization")String auth,@RequestBody Cursor q){s.read(WatchService.owner(auth),q.id());return Map.of("message","已保存阅读记录");}
}
