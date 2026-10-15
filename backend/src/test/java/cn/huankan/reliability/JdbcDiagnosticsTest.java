package cn.huankan.reliability;
import cn.huankan.collector.CollectorGuard;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JdbcDiagnosticsTest {
 @Test void databaseFailuresAreNotLabeledAsHttpTimeout(){assertEquals("DATABASE_CONNECTION_TIMEOUT",CollectorGuard.failure(new SQLTransientConnectionException("Connection timed out")));assertEquals("DATABASE_CONNECTION_FAILURE",CollectorGuard.failure(new SQLException("terminated","57P01")));assertEquals("DATABASE_QUERY_TIMEOUT",CollectorGuard.failure(new SQLException("cancelled","57014")));assertEquals("DATABASE_TRANSACTION_FAILURE",CollectorGuard.failure(new SQLException("deadlock","40P01")));assertEquals("REQUEST_TIMEOUT",CollectorGuard.failure(new java.net.http.HttpTimeoutException("request timed out")));}
 @Test void diagnosticMessagesRetainContextButRemoveSecrets(){String redacted=JdbcDiagnostics.redact("password=secret token=abc jdbc:postgresql://127.0.0.1:55432/huankan_test?password=hidden&user=test");assertFalse(redacted.contains("secret"));assertFalse(redacted.contains("abc"));assertFalse(redacted.contains("hidden"));assertTrue(redacted.contains("huankan_test"));}
}
