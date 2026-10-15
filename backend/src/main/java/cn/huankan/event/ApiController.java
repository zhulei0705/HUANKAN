package cn.huankan.event;

import cn.huankan.collector.CollectorService;
import cn.huankan.evidence.EvidenceService;
import cn.huankan.stream.StreamService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final QueryService query; private final CollectorService collector; private final StreamService stream;
    public ApiController(QueryService query,CollectorService collector,StreamService stream) {this.query=query;this.collector=collector;this.stream=stream;}
    public record Filter(String search,String source,int page,String region,String category,String sort) {}
    public record Source(String source,boolean force) {}
    @PostMapping("/events/query") public Object events(@RequestBody Filter f) {return query.events(f.search()==null?"":f.search(),f.source()==null?"":f.source(),f.page(),f.region()==null?"":f.region(),f.category()==null?"":f.category(),f.sort()==null?"official":f.sort());}
    @PostMapping("/stats") public Object stats() {return query.stats();}
    @PostMapping("/sources/query") public Object sources() {return query.sources();}
    @PostMapping("/monitor/alerts") public Object alerts() {return query.alerts();}
    @PostMapping("/sources/collect") public Object collect(@RequestBody Source s) {collector.collect(s.source(),s.force());return Map.of("message","采集任务已结束，请查看任务状态");}
    @PostMapping("/events/{id}/evidence") public Object evidence(@PathVariable UUID id) {return query.evidence(id);}
    @PostMapping("/evidence/{id}/raw") public ResponseEntity<byte[]> raw(@PathVariable UUID id) {
        var row=query.raw(id); byte[] bytes=(byte[])row.get("raw_content");
        if(!EvidenceService.hash(bytes).equals(row.get("raw_content_hash"))) throw new IllegalStateException("证据哈希不一致");
        return ResponseEntity.ok().header("Content-Type","application/octet-stream").header("Content-Disposition","attachment; filename=evidence-"+id+".bin").body(bytes);
    }
    @GetMapping(value="/stream",produces="text/event-stream") public SseEmitter stream(@RequestHeader(value="Last-Event-ID",defaultValue="0") long cursor,@RequestParam(defaultValue="OFFICIAL") String scope,@RequestParam(defaultValue="") String sources,@RequestParam(defaultValue="0") int priority,@RequestParam(defaultValue="false")boolean watchOnly,@RequestHeader(value="Authorization",defaultValue="")String authorization,jakarta.servlet.http.HttpServletResponse response) {var emitter=stream.connect(cursor,scope,sources,priority,watchOnly?WatchService.owner(authorization):"");response.setHeader("X-Stream-Connection-Id",stream.connectionId(emitter).toString());return emitter;}
    @PostMapping("/stream/cursor") public Object cursor() {return Map.of("cursor",Long.toString(stream.cursor()));}
    public record Receipt(UUID deliveryId,java.time.Instant receivedAt) {}
    public record Close(UUID connectionId,String kind) {}
    @PostMapping("/stream/close") public Object close(@RequestBody Close request){stream.clientClose(request.connectionId(),request.kind());return Map.of("message","关闭原因已记录");}
    @PostMapping("/stream/ack") public Object ack(@RequestBody Receipt receipt) {stream.acknowledge(receipt.deliveryId(),receipt.receivedAt());return Map.of("message","接收确认已记录");}
    @PostMapping("/stream/ack-batch") public Object ackBatch(@RequestBody java.util.List<Receipt> receipts) {if(receipts.size()>100)throw new IllegalArgumentException("确认批次超限");for(var receipt:receipts)stream.acknowledge(receipt.deliveryId(),receipt.receivedAt());return Map.of("message","接收确认已记录");}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) {return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> unavailable(IllegalStateException e) {return ResponseEntity.status(409).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(java.io.IOException.class) public void disconnected(java.io.IOException e) { /* Already classified by the stream service; the peer cannot receive an error body. */ }
}
