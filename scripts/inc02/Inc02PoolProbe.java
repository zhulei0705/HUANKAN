import com.sun.tools.attach.VirtualMachine;
import javax.management.*;
import javax.management.remote.*;
import java.util.*;
public class Inc02PoolProbe {
 public static void main(String[] args)throws Exception{
  VirtualMachine vm=VirtualMachine.attach(args[0]);String address;
  try{address=vm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");}finally{vm.detach();}
  if(address==null)throw new IllegalStateException("本地JMX未启动；不自动修改目标JVM配置");
  try(JMXConnector connector=JMXConnectorFactory.connect(new JMXServiceURL(address))){var server=connector.getMBeanServerConnection();var names=server.queryNames(new ObjectName("com.zaxxer.hikari:type=Pool (*)"),null);if(names.isEmpty())throw new IllegalStateException("没有Hikari池MBean，不能把缺失计为0");
   System.out.print("{\"pools\":[");boolean first=true;for(var n:names){if(!first)System.out.print(",");first=false;System.out.print("{\"name\":\""+n.getCanonicalName()+"\"");for(var attribute:List.of("ActiveConnections","IdleConnections","TotalConnections","ThreadsAwaitingConnection")){System.out.print(",\""+attribute+"\":"+server.getAttribute(n,attribute));}System.out.print("}");}System.out.println("]}");
  }
 }
}
