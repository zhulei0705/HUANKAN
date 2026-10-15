package cn.huankan.event;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ai-evaluation") public class AiEvaluationController {private final AiEvaluationService s;public AiEvaluationController(AiEvaluationService v){s=v;}@PostMapping("/prepare")public Object prepare(){return s.prepare();}@PostMapping("/status")public Object status(){return s.status();}}
