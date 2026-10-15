package cn.huankan.reliability;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Measures real JDBC calls without storing SQL, bind values, URLs or stack arguments. */
public final class ObservedDataSource extends DelegatingDataSource implements AutoCloseable {
    private final Supplier<MeterRegistry> registries;
    private final Map<String,Borrow> borrowed=new ConcurrentHashMap<>();
    private record Borrow(String correlation,String stage,long started) {}
    public ObservedDataSource(DataSource target,Supplier<MeterRegistry> registries){super(target);this.registries=registries;}
    public HikariDataSource hikari(){return (HikariDataSource)getTargetDataSource();}
    public List<Map<String,Object>> holders(){long now=System.nanoTime();return borrowed.entrySet().stream().map(e->{var b=e.getValue();return Map.<String,Object>of("connection_id",e.getKey(),"diagnostic_id",b.correlation(),"stage",b.stage(),"held_ms",(now-b.started())/1_000_000);}).toList();}
    private void duration(String name,long start,String outcome){var registry=registries.get();if(registry!=null)registry.timer(name,"outcome",outcome).record(System.nanoTime()-start,TimeUnit.NANOSECONDS);}
    private void count(String name){var registry=registries.get();if(registry!=null)registry.counter(name).increment();}
    @Override public Connection getConnection() throws SQLException {return acquire(()->super.getConnection());}
    @Override public Connection getConnection(String user,String password) throws SQLException {return acquire(()->super.getConnection(user,password));}
    private interface Acquisition {Connection get() throws SQLException;}
    private Connection acquire(Acquisition supplier) throws SQLException {
        long start=System.nanoTime();
        try {Connection target=supplier.get();duration("huankan.jdbc.acquire",start,"success");return connection(target);}
        catch(SQLException failure){duration("huankan.jdbc.acquire",start,"error");count("huankan.jdbc.errors");if(failure instanceof SQLTransientConnectionException)count("huankan.jdbc.acquire.timeouts");safeLog("获取连接失败",null,start,failure);throw failure;}
    }
    private Connection connection(Connection target){
        String id=UUID.randomUUID().toString();long acquired=System.nanoTime();borrowed.put(id,new Borrow(DiagnosticContext.id(),DiagnosticContext.stage(),acquired));
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},new InvocationHandler(){
            long transactionStart;boolean closed;
            @Override public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
                String name=method.getName();long start=System.nanoTime();
                if(name.equals("equals"))return proxy==args[0];if(name.equals("hashCode"))return System.identityHashCode(proxy);if(name.equals("toString"))return "已诊断JDBC连接/"+id;
                try{
                    Object result=call(target,method,args);
                    if(name.equals("setAutoCommit")){if(Boolean.FALSE.equals(args[0])&&transactionStart==0)transactionStart=System.nanoTime();else if(Boolean.TRUE.equals(args[0]))finishTransaction("autocommit");}
                    if(name.equals("commit")||name.equals("rollback")&& (args==null||args.length==0))finishTransaction(name);
                    if(result instanceof Statement statement)return statement(statement,(Connection)proxy,id);
                    return result;
                }catch(SQLException failure){count("huankan.jdbc.errors");safeLog("连接或事务失败",id,start,failure);throw failure;}
                finally {if(name.equals("close")&&!closed){closed=true;finishTransaction("close");borrowed.remove(id);duration("huankan.jdbc.hold",acquired,"released");}}
            }
            private void finishTransaction(String outcome){if(transactionStart!=0){duration("huankan.jdbc.transaction",transactionStart,outcome);safeLog("事务结束/"+outcome,id,transactionStart,null);transactionStart=0;}}
        });
    }
    private Statement statement(Statement target,Connection connection,String id){
        Class<?> type=target instanceof CallableStatement?CallableStatement.class:target instanceof PreparedStatement?PreparedStatement.class:Statement.class;
        return (Statement)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            String name=method.getName();if(name.equals("getConnection"))return connection;
            if(name.equals("toString"))return "已诊断JDBC语句/"+id;
            if(name.equals("equals"))return proxy==args[0];if(name.equals("hashCode"))return System.identityHashCode(proxy);
            boolean execute=name.startsWith("execute");long start=System.nanoTime();
            try{Object result=call(target,method,args);if(execute){duration("huankan.jdbc.sql",start,"success");if(System.nanoTime()-start>=100_000_000)safeLog("慢SQL阶段/"+name,id,start,null);}return result;}
            catch(SQLException failure){if(execute){duration("huankan.jdbc.sql",start,"error");count("huankan.jdbc.errors");safeLog("SQL阶段失败/"+name,id,start,failure);}throw failure;}
        });
    }
    private static Object call(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
    private void safeLog(String phase,String connection,long start,SQLException failure){
        // SQLException messages can include SQL literals: record type/state/code only here.
        LoggerFactory.getLogger(getClass()).info("数据库阶段：关联={}，服务={}，连接={}，阶段={}，耗时={}毫秒，异常类型={}，SQLState={}，错误码={}",DiagnosticContext.id(),DiagnosticContext.stage(),connection,phase,(System.nanoTime()-start)/1_000_000,failure==null?null:failure.getClass().getName(),failure==null?null:failure.getSQLState(),failure==null?null:failure.getErrorCode());
    }
    @Override public void close(){hikari().close();}
}
