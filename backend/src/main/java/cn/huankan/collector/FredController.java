package cn.huankan.collector;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/fred")
public class FredController {
    private final FredCollector collector;public FredController(FredCollector collector){this.collector=collector;}
    @PostMapping("/status") public Object status(){return collector.status();}
}
