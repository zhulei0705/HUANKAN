package cn.huankan.collector;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sec")
public class SecController {
    private final SecCompanyService companies;
    public SecController(SecCompanyService companies){this.companies=companies;}
    record Search(String search){} record Follow(String cik){}
    @PostMapping("/status") public Object status(){return companies.status();}
    @PostMapping("/companies/refresh") public Object refresh(){return companies.refresh();}
    @PostMapping("/companies/search") public Object search(@RequestBody Search s){return companies.search(s.search()==null?"":s.search());}
    @PostMapping("/companies/follow") public Object follow(@RequestBody Follow f){return companies.follow(f.cik());}
}
