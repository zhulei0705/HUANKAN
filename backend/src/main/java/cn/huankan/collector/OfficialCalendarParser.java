package cn.huankan.collector;
import java.time.*;import java.util.*;import java.util.regex.*;
public final class OfficialCalendarParser {
 public record Entry(LocalDate date,String title,String titleZh,String excerpt,String plannedTime){public Entry(LocalDate d,String t,String z,String e){this(d,t,z,e,null);}}
 public static List<Entry> parse(String source,String html){
  if(source.equals("FED"))return fed(html);
  if(!source.equals("ECB"))return List.of();
  var result=new ArrayList<Entry>();var p=Pattern.compile("(?is)<dt[^>]*>\\s*(\\d{2}/\\d{2}/\\d{4})\\s*</dt>\\s*<dd[^>]*>(.*?)</dd>").matcher(html);
  while(p.find()){String title=p.group(2).replaceAll("<[^>]+>"," ").replace("&amp;","&").replaceAll("\\s+"," ").strip();if(title.isBlank())continue;String zh=title.contains("Press conference")?"欧洲央行政策会议新闻发布会":title.contains("non-monetary")?"欧洲央行非货币政策会议":title.contains("General Council")?"欧洲央行总理事会会议":title.contains("monetary policy")?"欧洲央行货币政策会议":"欧洲央行官方会议";result.add(new Entry(LocalDate.parse(p.group(1),java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")),title,zh,p.group()));}
  return result;
 }
 private static List<Entry> fed(String html){
  var month=Pattern.compile("(?is)<div class=\"row-title text-center\">\\s*<h4[^>]*>([A-Za-z]+ [0-9]{4})</h4>").matcher(html);if(!month.find())return List.of();
  YearMonth ym=YearMonth.parse(month.group(1),java.time.format.DateTimeFormatter.ofPattern("MMMM uuuu",Locale.ENGLISH));
  var p=Pattern.compile("(?is)(?:<div class=\"col-xs-2\">\\s*<p>(?<time>.*?)</p>\\s*</div>\\s*)?<div class=\"col-xs-7\">\\s*(?<body>.*?)</div>\\s*<div class=\"col-xs-3\">\\s*<p>\\s*(?<days>[0-9, ]+)\\s*</p>").matcher(html);var result=new ArrayList<Entry>();
  while(p.find()){var title=Pattern.compile("(?is)<p>(.*?)</p>").matcher(p.group("body"));if(!title.find())continue;String official=title.group(1).replaceAll("<[^>]+>"," ").replace("&amp;","&").replaceAll("\\s+"," ").strip();if(official.isEmpty())continue;
   String zh=official.startsWith("G.")||official.startsWith("H.")?"美联储官方统计计划发布":"美联储官方活动计划";if(p.group("body").contains("approximate"))zh+="（日期为官方预估）";
   for(String d:p.group("days").split(",")){int day=Integer.parseInt(d.strip());if(day<1||day>ym.lengthOfMonth())throw new IllegalArgumentException("官方日历日期不合法");result.add(new Entry(ym.atDay(day),official,zh,p.group(),p.group("time")==null||p.group("time").isBlank()?null:p.group("time").strip()));}
  }return result;
 }
}
