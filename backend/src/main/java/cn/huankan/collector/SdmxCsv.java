package cn.huankan.collector;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public final class SdmxCsv {
    private SdmxCsv(){}
    public record Observation(Map<String,String> metadata,LocalDate date,BigDecimal value,String status){}
    public static List<List<String>> rows(String csv){
        var result=new ArrayList<List<String>>();var row=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<csv.length();i++){char c=csv.charAt(i);if(c=='"'){if(quoted&&i+1<csv.length()&&csv.charAt(i+1)=='"'){cell.append('"');i++;}else if(quoted||cell.isEmpty())quoted=!quoted;else throw new IllegalArgumentException("CSV引号格式异常");}else if(!quoted&&(c==','||c=='\n')){row.add(cell.toString().replaceAll("\r$",""));cell.setLength(0);if(c=='\n'){if(!(row.size()==1&&row.getFirst().isEmpty()))result.add(List.copyOf(row));row.clear();}}else cell.append(c);}
        if(quoted)throw new IllegalArgumentException("CSV引号未闭合");if(!cell.isEmpty()||!row.isEmpty()){row.add(cell.toString().replaceAll("\r$",""));result.add(List.copyOf(row));}return result;
    }
    public static List<Observation> parse(byte[] bytes,String expected){
        var rows=rows(new String(bytes,StandardCharsets.UTF_8));if(rows.isEmpty()||rows.size()>10001)throw new IllegalArgumentException("SDMX数据为空或超限");var header=rows.getFirst();
        boolean policy=expected.startsWith("FM.");
        var required=new ArrayList<>(List.of("KEY","FREQ","CURRENCY","TIME_PERIOD","OBS_VALUE","OBS_STATUS","UNIT","UNIT_MULT","TITLE"));required.addAll(policy?List.of("REF_AREA","PROVIDER_FM","INSTRUMENT_FM","PROVIDER_FM_ID","DATA_TYPE_FM"):List.of("CURRENCY_DENOM","EXR_TYPE","EXR_SUFFIX"));
        if(new HashSet<>(header).size()!=header.size()||!header.containsAll(required))throw new IllegalArgumentException("SDMX结构缺失");
        var result=new ArrayList<Observation>();var dates=new HashSet<LocalDate>();
        for(var row:rows.subList(1,rows.size())){if(row.size()!=header.size())throw new IllegalArgumentException("SDMX列数不一致");var values=new LinkedHashMap<String,String>();for(int i=0;i<header.size();i++)values.put(header.get(i),row.get(i));
            String dimensions=policy?String.join(".",values.get("FREQ"),values.get("REF_AREA"),values.get("CURRENCY"),values.get("PROVIDER_FM"),values.get("INSTRUMENT_FM"),values.get("PROVIDER_FM_ID"),values.get("DATA_TYPE_FM")):String.join(".",values.get("FREQ"),values.get("CURRENCY"),values.get("CURRENCY_DENOM"),values.get("EXR_TYPE"),values.get("EXR_SUFFIX"));
            boolean units=policy?"B".equals(values.get("FREQ"))&&"U2".equals(values.get("REF_AREA"))&&"EUR".equals(values.get("CURRENCY"))&&"4F".equals(values.get("PROVIDER_FM"))&&"KR".equals(values.get("INSTRUMENT_FM"))&&"PCPA".equals(values.get("UNIT")):"D".equals(values.get("FREQ"))&&"EUR".equals(values.get("CURRENCY_DENOM"))&&values.get("CURRENCY").equals(values.get("UNIT"));
            if(!expected.equals(values.get("KEY"))||!expected.equals((policy?"FM.":"EXR.")+dimensions)||!units||!"0".equals(values.get("UNIT_MULT")))throw new IllegalArgumentException("SDMX币种、序列或单位不匹配");
            LocalDate date=LocalDate.parse(values.get("TIME_PERIOD"));if(!dates.add(date)||date.isAfter(LocalDate.now(java.time.ZoneOffset.UTC)))throw new IllegalArgumentException("SDMX日期重复或未来观测");
            result.add(new Observation(Collections.unmodifiableMap(values),date,values.get("OBS_VALUE").isBlank()?null:new BigDecimal(values.get("OBS_VALUE")),values.get("OBS_STATUS")));
        }return List.copyOf(result);
    }
    public static void validateStructure(byte[] xml){
        validateStructure(xml,"EXR","ECB_EXR1",List.of("FREQ","CURRENCY","CURRENCY_DENOM","EXR_TYPE","EXR_SUFFIX"));
    }
    public static void validatePolicyStructure(byte[] xml){
        validateStructure(xml,"FM","ECB_FMD2",List.of("FREQ","REF_AREA","CURRENCY","PROVIDER_FM","INSTRUMENT_FM","PROVIDER_FM_ID","DATA_TYPE_FM"));
        // Codes are checked inside their official codelists, not matched against arbitrary XML text.
        try{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");var doc=f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));var lists=doc.getElementsByTagNameNS("http://www.sdmx.org/resources/sdmxml/schemas/v2_1/structure","Codelist");var required=Map.of("CL_PROVIDER_FM_ID",Set.of("DFR","MRR_FR","MLFR"),"CL_PROVIDER_FM",Set.of("4F"),"CL_INSTRUMENT_FM",Set.of("KR"),"CL_FREQ",Set.of("B"),"CL_UNIT",Set.of("PCPA"));var confirmed=new HashSet<String>();for(int i=0;i<lists.getLength();i++){var list=(org.w3c.dom.Element)lists.item(i);if(!required.containsKey(list.getAttribute("id")))continue;var codes=list.getElementsByTagNameNS("*","Code");var ids=new HashSet<String>();for(int j=0;j<codes.getLength();j++)ids.add(((org.w3c.dom.Element)codes.item(j)).getAttribute("id"));if(ids.containsAll(required.get(list.getAttribute("id"))))confirmed.add(list.getAttribute("id"));}if(confirmed.size()!=required.size())throw new IllegalArgumentException("政策利率官方代码缺失");}catch(Exception e){throw new IllegalArgumentException("政策利率代码核验失败",e);}
    }
    private static void validateStructure(byte[] xml,String flow,String structure,List<String> dimensions){
        try{var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");var doc=f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
            var flows=doc.getElementsByTagNameNS("*","Dataflow");boolean found=false;for(int i=0;i<flows.getLength();i++){var e=(org.w3c.dom.Element)flows.item(i);if(flow.equals(e.getAttribute("id"))&&"ECB".equals(e.getAttribute("agencyID")))found=true;}if(!found)throw new IllegalArgumentException("官方Dataflow缺失");
            String structureNs="http://www.sdmx.org/resources/sdmxml/schemas/v2_1/structure";
            var structures=doc.getElementsByTagNameNS(structureNs,"DataStructure");boolean matched=false;for(int i=0;i<structures.getLength();i++){var e=(org.w3c.dom.Element)structures.item(i);if(!structure.equals(e.getAttribute("id")))continue;var lists=e.getElementsByTagNameNS(structureNs,"DimensionList");if(lists.getLength()!=1)throw new IllegalArgumentException("SDMX维度列表缺失");var nodes=((org.w3c.dom.Element)lists.item(0)).getElementsByTagNameNS(structureNs,"Dimension");var actual=new TreeMap<Integer,String>();for(int j=0;j<nodes.getLength();j++){var d=(org.w3c.dom.Element)nodes.item(j);if(actual.put(Integer.parseInt(d.getAttribute("position")),d.getAttribute("id"))!=null)throw new IllegalArgumentException("SDMX维度位置重复");}matched=List.copyOf(actual.values()).equals(dimensions);}if(!matched)throw new IllegalArgumentException("官方维度顺序变化");
        }catch(Exception e){throw new IllegalArgumentException("SDMX结构校验失败",e);}
    }
}
