package cn.huankan.collector;

import cn.huankan.evidence.EvidenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecCompanyService {
    private final JdbcTemplate db;private final SecConfiguration config;private final OfficialHttpClient http;
    public SecCompanyService(JdbcTemplate db,SecConfiguration config,OfficialHttpClient http){this.db=db;this.config=config;this.http=http;}
    public Object status(){var status=new java.util.HashMap<String,Object>();status.put("state",config.state(!config.followedCiks().isBlank()||Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM sec_followed_company)",Boolean.class))));status.put("contact_configured",config.hasContact());status.put("enabled",config.enabled());status.put("followed",db.queryForList("SELECT f.cik,r.title,r.ticker FROM sec_followed_company f JOIN sec_company_registry r ON r.cik=f.cik"));status.put("mapping_count",db.queryForObject("SELECT count(*) FROM sec_company_registry",Long.class));status.put("mapping_acquired_at",db.queryForObject("SELECT max(collected_at) FROM sec_company_registry",java.sql.Timestamp.class));status.put("license_approved",db.queryForObject("SELECT status='APPROVED' AND (valid_until IS NULL OR valid_until>now()) FROM source_license WHERE source_id='SEC'",Boolean.class));return status;}
    @Transactional public Object refresh(){
        String agent=config.userAgent();
        String url="https://www.sec.gov/files/company_tickers.json";
        var result=http.get(URI.create(url),agent,Map.of());
        if(result.status()!=200)throw new IllegalStateException("SEC公司映射HTTP_"+result.status());
        try {
            var root=new ObjectMapper().readTree(result.body());
            if(!root.isObject()||root.size()>50000)throw new IllegalArgumentException("SEC映射结构变化");
            String rawHash=EvidenceService.hash(result.body());
            db.update("INSERT INTO sec_mapping_evidence(source_url,raw_response,sha256) VALUES(?,?,?)",url,result.body(),rawHash);
            for(var row:root){String cik=row.path("cik_str").asText(),title=row.path("title").asText(),ticker=row.path("ticker").asText();
                if(!cik.matches("[0-9]{1,10}")||title.isBlank()||ticker.isBlank())throw new IllegalArgumentException("SEC映射字段缺失");
                db.update("INSERT INTO sec_company_registry VALUES(?,?,?,?,now(),?) ON CONFLICT(cik) DO UPDATE SET title=excluded.title,ticker=excluded.ticker,collected_at=excluded.collected_at,raw_hash=excluded.raw_hash",String.format("%010d",Long.parseLong(cik)),title,ticker,url,rawHash);
            }
            db.update("INSERT INTO audit_log(actor,action,target) VALUES('local-admin','SEC_MAPPING_REFRESH','SEC')");
            return Map.of("count",root.size());
        }catch(java.io.IOException e){throw new IllegalArgumentException("SEC映射解析失败");}
    }
    public Object search(String text){if(text.length()>100)throw new IllegalArgumentException("搜索文字过长");return db.queryForList("SELECT cik,title,ticker,source_url,collected_at FROM sec_company_registry WHERE title ILIKE '%'||?||'%' OR ticker ILIKE '%'||?||'%' OR cik=? ORDER BY ticker LIMIT 30",text,text,text);}
    @Transactional public Object follow(String cik){
        if(!cik.matches("[0-9]{10}"))throw new IllegalArgumentException("CIK必须来自官方公司搜索结果");
        db.update("INSERT INTO sec_followed_company(cik) VALUES(?) ON CONFLICT DO NOTHING",cik);
        db.update("INSERT INTO audit_log(actor,action,target) VALUES('local-admin','SEC_FOLLOW',?)",cik);
        return status();
    }
}

