package cn.huankan.collector;

import java.time.*;
import java.time.format.*;
import java.util.*;

/** Only official index records authorize file downloads; candidate slots never do. */
public final class GdeltIndex {
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    public record Entry(String batch,String kind,long bytes,String md5,String url) {
        public Instant time(){return LocalDateTime.parse(batch,FORMAT).toInstant(ZoneOffset.UTC);}
        public boolean downloadable(Instant now){return !time().isAfter(now);}
    }
    public static Entry line(String line){
        String[] f=line.strip().split("\\s+");
        if(f.length!=3||!f[0].matches("[0-9]{1,10}")||!f[1].matches("[a-fA-F0-9]{32}")||!f[2].matches("https?://data[.]gdeltproject[.]org/gdeltv2/[0-9]{14}[.](export[.]CSV|mentions[.]CSV|gkg[.]csv)[.]zip"))throw new IllegalArgumentException("官方索引字段无效");
        String name=f[2].substring(f[2].lastIndexOf('/')+1), batch=name.substring(0,14),kind=name.contains(".gkg.")?"gkg":name.contains(".mentions.")?"mentions":"export";
        var entry=new Entry(batch,kind,Long.parseLong(f[0]),f[1].toLowerCase(Locale.ROOT),f[2].replace("http://","https://"));
        try{entry.time();}catch(DateTimeException e){throw new IllegalArgumentException("官方批次时间无效",e);}if(entry.bytes()<1)throw new IllegalArgumentException("官方文件大小无效");
        return entry;
    }
    public static List<Entry> latest(byte[] data){
        var result=new ArrayList<Entry>();for(String line:new String(data,java.nio.charset.StandardCharsets.US_ASCII).split("\\R"))if(!line.isBlank())result.add(line(line));
        if(result.size()!=3||result.stream().map(Entry::kind).distinct().count()!=3||result.stream().map(Entry::batch).distinct().count()!=1)throw new IllegalArgumentException("最新索引必须包含同一批次的三类文件");return List.copyOf(result);
    }
    public static List<String> slots(String from,String to,int limit){
        var list=new ArrayList<String>();Instant next=LocalDateTime.parse(from,FORMAT).toInstant(ZoneOffset.UTC).plusSeconds(900),end=LocalDateTime.parse(to,FORMAT).toInstant(ZoneOffset.UTC);
        while(next.isBefore(end)&&list.size()<limit){list.add(FORMAT.format(next.atOffset(ZoneOffset.UTC)));next=next.plusSeconds(900);}return list;
    }
    public static String failure(Throwable error,String batch,Instant now){
        String message=String.valueOf(error.getMessage());
        if(message.contains("HTTP_404"))return batch!=null&&LocalDateTime.parse(batch,FORMAT).toInstant(ZoneOffset.UTC).isBefore(now.minusSeconds(3600))?"MISSING_HISTORICAL_BATCH":"PUBLISHED_NOT_AVAILABLE";
        if(message.contains("MD5")||message.contains("校验")||error instanceof IllegalArgumentException||message.contains("HTTP_403"))return "PERMANENT_FAILURE";
        return "TRANSIENT_HTTP_FAILURE";
    }
}
