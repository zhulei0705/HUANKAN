package cn.huankan.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class SecAdapter implements SourceAdapter {
    @Override public List<Item> parse(byte[] bytes) {
        try {
            var root = new ObjectMapper().readTree(bytes);
            String cik = root.path("cik").asText();
            if (!cik.matches("[0-9]{1,10}")) throw new IllegalArgumentException("CIK无效");
            var r = root.path("filings").path("recent");
            var a = r.path("accessionNumber");
            if (!a.isArray() || a.size()>5000) throw new IllegalArgumentException("SEC结构变化");
            for(String key : List.of("form","primaryDocument","acceptanceDateTime")) if(!r.path(key).isArray() || r.path(key).size()!=a.size()) throw new IllegalArgumentException("SEC列长度不一致");
            var items = new ArrayList<Item>();
            for(int i=0;i<a.size();i++) {
                String id=a.get(i).asText(), file=r.path("primaryDocument").get(i).asText();
                if(!id.matches("[0-9]{10}-[0-9]{2}-[0-9]{6}") || !file.matches("[a-zA-Z0-9_.-]+")) throw new IllegalArgumentException("申报标识无效");
                String time=r.path("acceptanceDateTime").get(i).asText();
                String form=r.path("form").get(i).asText();
                if(!form.matches("[A-Za-z0-9 /.-]{1,40}"))throw new IllegalArgumentException("申报类型无效");
                items.add(new Item(id,root.path("name").asText()+" · "+form,"https://www.sec.gov/Archives/edgar/data/"+Long.parseLong(cik)+"/"+id.replace("-","")+"/"+file,time.isBlank()?null:OffsetDateTime.parse(time).toInstant(),form.endsWith("/A")?"企业修订申报":"企业申报"));
            }
            return List.copyOf(items);
        } catch(Exception e) { throw new IllegalArgumentException("SEC证据校验失败", e); }
    }
}
