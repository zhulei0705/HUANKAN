package cn.huankan.collector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Parses GKG 2.1 metadata, never downloads underlying article text. */
public final class GdeltParser {
    public record Lead(int line,String documentUrl,String publisher,String themes,String organizations,String countries,byte[] raw) {}
    public record Parsed(List<Lead> leads,int total,int excluded) {}
    public static Parsed parse(byte[] zip,String batch) throws IOException {
        if(zip.length>10*1024*1024)throw new IOException("GDELT压缩文件超过10MiB");
        var leads=new ArrayList<Lead>();int lines=0,excluded=0;
        try(var input=new ZipInputStream(new ByteArrayInputStream(zip))){
            var entry=input.getNextEntry();
            if(entry==null||!entry.getName().equals(batch+".gkg.csv")||entry.isDirectory())throw new IOException("GDELT压缩条目名称异常");
            var bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;
            while((read=input.read(buffer))!=-1){if(bytes.size()+read>80*1024*1024)throw new IOException("GDELT解压超过80MiB");bytes.write(buffer,0,read);}
            if(input.getNextEntry()!=null)throw new IOException("GDELT压缩条目数量异常");
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            String text=decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
            for(String line:text.split("\n")){
                lines++;if(lines>100000||line.length()>1024*1024)throw new IOException("GDELT行数量或长度超限");
                String[] f=(line.endsWith("\r")?line.substring(0,line.length()-1):line).split("\t",-1);
                if(f.length!=27||!f[0].matches("[0-9]{14}-[A-Z]?[0-9]+")||!f[1].matches("[0-9]{14}"))throw new IOException("GDELT列或记录标识异常，第"+lines+"行");
                String tags=(f[7]+";"+f[13]).toUpperCase(Locale.ROOT);
                if(!tags.matches("(?s).*(ECON_|CENTRAL_BANK|FINANC|BANK|TRADE|ENERGY|SEMICONDUCT|ARTIFICIAL_INTELLIGENCE|NVIDIA|MICROSOFT|APPLE|BROADCOM|ADVANCED MICRO DEVICES|INDUSTRY).*")||!safeLink(f[4])){excluded++;continue;}
                var countries=new TreeSet<String>();for(String location:f[9].split(";")){var parts=location.split("#",-1);if(parts.length>=3&&parts[2].matches("[A-Z]{2}"))countries.add(parts[2]);}
                leads.add(new Lead(lines,f[4],f[3],f[7],f[13],String.join(",",countries),line.getBytes(StandardCharsets.UTF_8)));
            }
        }
        return new Parsed(List.copyOf(leads),lines,excluded);
    }
    public static boolean safeLink(String url){try{var u=java.net.URI.create(url);return Set.of("http","https").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getPort()==-1;}catch(Exception e){return false;}}
}
