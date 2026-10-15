package cn.huankan.event;
import java.util.Locale;
/** A percentage point is not a percent; money magnitudes are not interchangeable. */
public final class FinancialUnitGuard {
 private FinancialUnitGuard(){}
 public static boolean consistent(String unit,String quote){String u=unit.toLowerCase(Locale.ROOT).strip(),q=quote.toLowerCase(Locale.ROOT);
  boolean points=q.contains("percentage point")||q.contains("百分点")||q.contains("basis point")||q.contains("基点");
  if(u.equals("percent")||u.equals("%")||u.equals("百分比"))return !points&&(q.matches("(?s).*\\bpercent\\b.*")||q.contains("%"));
  if(u.equals("percentage points")||u.equals("percentage point")||u.equals("百分点"))return q.contains("percentage point")||q.contains("百分点");
  if(u.equals("basis points")||u.equals("basis point")||u.equals("基点"))return q.contains("basis point")||q.contains("基点");
  return !u.isEmpty()&&q.contains(u);
 }
}
