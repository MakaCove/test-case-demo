package com.testcase.backend.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从上传的需求文档中提取纯文本，写入 {@code requirement_assets.content}。
 */
@Service
public class DocumentTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);
    /** 防止超大文档撑爆内存与模型上下文 */
    private static final int MAX_CHARS = 500_000;

    private final AutoDetectParser parser = new AutoDetectParser();
    private final Tika tika = new Tika();

    public String extractAsPlainText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String name = file.getOriginalFilename();
        String safeName = StringUtils.hasText(name) ? name : "upload.bin";
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        try {
            String text = extractFromBytes(bytes, safeName, file.getContentType());
            text = normalize(text);
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS) + "\n\n...[truncated]";
            }
            return text;
        } catch (SAXException | TikaException e) {
            log.warn("tika extract failed, fileName={}, err={}", safeName, e.getMessage());
            throw new IllegalArgumentException("文档解析失败：" + e.getMessage());
        }
    }

    private String extractFromBytes(byte[] bytes, String fileName, String contentTypeHint)
            throws IOException, SAXException, TikaException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".xml")) {
            String raw = new String(bytes, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(raw)) {
                throw new IllegalArgumentException("文本文件为空");
            }
            return raw;
        }

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BodyContentHandler handler = new BodyContentHandler(MAX_CHARS * 2);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            if (StringUtils.hasText(contentTypeHint)) {
                metadata.set(Metadata.CONTENT_TYPE, contentTypeHint);
            }
            parser.parse(in, handler, metadata, new ParseContext());
            String out = handler.toString();
            if (StringUtils.hasText(out)) {
                return out;
            }
        }
        try (InputStream in2 = new ByteArrayInputStream(bytes)) {
            return tika.parseToString(in2);
        }
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\u0000", "").trim();
    }
}
