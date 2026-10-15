package cn.huankan.event;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/relations")public class RelationReviewController {
 private final RelationReviewService service;public RelationReviewController(RelationReviewService s){service=s;}
 @PostMapping("/{id}/review")public Object review(@PathVariable UUID id,@RequestBody RelationReviewService.Review q){service.review(id,q);return Map.of("message","审核已记录，原始证据与拒绝依据保留");}
 @PostMapping("/{id}/history")public Object history(@PathVariable UUID id){return service.history(id);}
 @ExceptionHandler(IllegalArgumentException.class)public org.springframework.http.ResponseEntity<?> invalid(IllegalArgumentException e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
