package cn.huankan.reliability;

import java.util.UUID;
import org.slf4j.MDC;

/** Only generated identifiers and code locations; never request parameters or user identity. */
public final class DiagnosticContext implements AutoCloseable {
    private final String previousId, previousStage;
    public DiagnosticContext(String stage) {
        this(stage,null);
    }
    private DiagnosticContext(String stage,String parentId) {
        previousId=MDC.get("diagnostic_id"); previousStage=MDC.get("diagnostic_stage");
        if(previousId==null) MDC.put("diagnostic_id",parentId==null?UUID.randomUUID().toString():parentId);
        MDC.put("diagnostic_stage",stage);
    }
    public static DiagnosticContext continueInternal(String stage,String parentId){
        try{UUID.fromString(parentId);return new DiagnosticContext(stage,parentId);}catch(IllegalArgumentException failure){return new DiagnosticContext(stage);}
    }
    public static String id(){String id=MDC.get("diagnostic_id");return id==null?"无上游关联":id;}
    public static String stage(){String stage=MDC.get("diagnostic_stage");return stage==null?"数据库直接调用":stage;}
    @Override public void close(){restore("diagnostic_id",previousId);restore("diagnostic_stage",previousStage);}
    private static void restore(String key,String value){if(value==null)MDC.remove(key);else MDC.put(key,value);}
}
