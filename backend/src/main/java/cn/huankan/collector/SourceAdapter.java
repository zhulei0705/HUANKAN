package cn.huankan.collector;

import java.time.Instant;
import java.util.List;

public interface SourceAdapter {
    record Item(String documentId, String title, String url, Instant publishedAt, String category) {}
    List<Item> parse(byte[] bytes);
}
