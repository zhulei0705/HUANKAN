package cn.huankan.collector;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

public class RssAdapter implements SourceAdapter {
    private final String host; public RssAdapter(String host){this.host=host;}
    @Override public List<Item> parse(byte[] bytes) {
        try {
            var f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            boolean atom="feed".equals(doc.getDocumentElement().getLocalName());
            if (!atom && !doc.getDocumentElement().getTagName().equals("rss")) throw new IllegalArgumentException("RSS结构变化");
            var nodes = atom?doc.getElementsByTagNameNS("http://www.w3.org/2005/Atom","entry"):doc.getElementsByTagName("item");
            if(nodes.getLength() > 1000) throw new IllegalArgumentException("RSS条目超限");
            var items = new ArrayList<Item>();
            for(int i=0;i<nodes.getLength();i++) {
                var e = (Element) nodes.item(i);
                String title = text(e,"title"), url = text(e,"link"), guid = text(e,"guid");
                if(atom){guid=text(e,"id");var links=e.getElementsByTagNameNS("http://www.w3.org/2005/Atom","link");url="";for(int k=0;k<links.getLength();k++){var link=(Element)links.item(k);if(link.getAttribute("rel").isBlank()||link.getAttribute("rel").equals("alternate")){url=link.getAttribute("href");break;}}}
                var uri = URI.create(url);
                if(!"https".equals(uri.getScheme()) || !host.equals(uri.getHost()) || uri.getUserInfo()!=null || uri.getPort()!=-1) throw new IllegalArgumentException("来源链接不匹配");
                if(title.isBlank()) throw new IllegalArgumentException("标题缺失");
                String time = text(e,atom?"published":"pubDate");
                String category=url.contains("/press/key/")?"管理层讲话":url.contains("/press/stats/")?"统计新闻":url.contains("/press/accounts/")||url.contains("/press/govcdec/")?"货币政策":"官方新闻";
                var published=time.isBlank()?null:host.equals("www.stats.gov.cn")?java.time.LocalDateTime.parse(time,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant():atom?java.time.OffsetDateTime.parse(time).toInstant():ZonedDateTime.parse(time,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                items.add(new Item(host.equals("www.stats.gov.cn")?url:guid.isBlank()?url:guid,title,url,published,host.equals("www.stats.gov.cn")?"官方宏观公告":category));
            }
            return List.copyOf(items);
        } catch(Exception e) { throw new IllegalArgumentException("RSS证据校验失败", e); }
    }
    private static String text(Element e,String key) { var n=e.getElementsByTagNameNS("*",key); return n.getLength()==0?"":n.item(0).getTextContent().strip(); }
}

