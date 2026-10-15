package cn.huankan.event;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/relation-assessment") public class RelationAssessmentController {private final RelationAssessmentService s;public RelationAssessmentController(RelationAssessmentService v){s=v;}@PostMapping("/audit")public Object audit(){return s.audit();}@PostMapping("/history")public Object history(){return s.history();}}
