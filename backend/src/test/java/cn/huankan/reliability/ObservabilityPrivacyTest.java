package cn.huankan.reliability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservabilityPrivacyTest {
    @Test void internalAsyncContextRetainsGeneratedParentAndClearsThread(){
        String parent;try(var context=new DiagnosticContext("隔离任务")){parent=DiagnosticContext.id();}
        try(var heartbeat=DiagnosticContext.continueInternal("隔离心跳",parent)){assertEquals(parent,DiagnosticContext.id());assertEquals("隔离心跳",DiagnosticContext.stage());}
        assertEquals("无上游关联",DiagnosticContext.id());
    }
    @Test void diagnosticChainIncludesJdbcNextExceptionAndSuppressed(){
        var failure=new java.sql.SQLException("batch","08006",1);failure.setNextException(new java.sql.SQLException("next","57014",2));failure.addSuppressed(new IllegalStateException("suppressed"));
        var evidence=JdbcDiagnostics.evidence("隔离异常链","FAILED",new JdbcTemplate(mock(HikariDataSource.class)),failure);
        assertEquals(3,((java.util.List<?>)evidence.get("exceptions")).size());assertTrue(evidence.toString().contains("57014"));
    }
    @Test void generatedHttpCorrelationIgnoresUserHeaderAndClearsThread() throws Exception {
        var request=new MockHttpServletRequest();request.addHeader("X-Huankan-Diagnostic-Id","private-user-id");request.setQueryString("password=private-password");var response=new MockHttpServletResponse();
        new ExecutionDiagnostics(new SimpleMeterRegistry()).doFilter(request,response,(r,s)->assertNotEquals("无上游关联",DiagnosticContext.id()));
        assertNotEquals("private-user-id",response.getHeader("X-Huankan-Diagnostic-Id"));assertDoesNotThrow(()->java.util.UUID.fromString(response.getHeader("X-Huankan-Diagnostic-Id")));assertEquals("无上游关联",DiagnosticContext.id());
    }
    @Test void acquisitionFailureLogsNeverIncludeDriverSqlOrCredentials() throws Exception {
        var pool=mock(HikariDataSource.class);when(pool.getConnection()).thenThrow(new SQLTransientConnectionException("SELECT 'private-user-value'; password=private-password","08006",77));var metrics=new SimpleMeterRegistry();
        var logger=(Logger)LoggerFactory.getLogger(ObservedDataSource.class);var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try{assertThrows(SQLTransientConnectionException.class,()->new ObservedDataSource(pool,()->metrics).getConnection());assertEquals(1,metrics.get("huankan.jdbc.acquire.timeouts").counter().count());String text=appender.list.toString()+appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();assertFalse(text.contains("private-user-value"));assertFalse(text.contains("private-password"));assertTrue(text.contains("08006"));assertTrue(text.contains("77"));}finally{logger.detachAppender(appender);}
    }
    @Test void ordinaryFailureDiagnosticKeepsTypeStateCodeAndOmitsSensitiveMessage(){
        var pool=mock(HikariDataSource.class);var logger=(Logger)LoggerFactory.getLogger(JdbcDiagnostics.class);var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try{JdbcDiagnostics.failure("隔离隐私测试","FAILED",new JdbcTemplate(pool),new java.sql.SQLException("SELECT 'private-user-value'","57014",99));String text=appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",String::concat);assertFalse(text.contains("private-user-value"));assertTrue(text.contains("57014"));assertTrue(text.contains("99"));}finally{logger.detachAppender(appender);}
    }
}
