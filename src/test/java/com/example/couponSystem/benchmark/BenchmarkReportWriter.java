package com.example.couponSystem.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

final class BenchmarkReportWriter {
    private static final Path REPORT_DIR = Path.of("build", "reports", "startup-benchmark");

    private BenchmarkReportWriter() {
    }

    static void write(String baseFileName, Map<String, Object> payload, String markdown) {
        try {
            Files.createDirectories(REPORT_DIR);

            Path jsonPath = REPORT_DIR.resolve(baseFileName + ".json");
            Files.writeString(
                    jsonPath,
                    toJson(payload, 0),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            Path markdownPath = REPORT_DIR.resolve(baseFileName + ".md");
            Files.writeString(
                    markdownPath,
                    markdown,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write benchmark report files.", e);
        }
    }

    private static String toJson(Object value, int indent) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return '"' + escape(str) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            builder.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i++ > 0) {
                    builder.append(",\n");
                }
                builder.append(" ".repeat(indent + 2))
                        .append('"').append(escape(String.valueOf(entry.getKey()))).append('"')
                        .append(": ")
                        .append(toJson(entry.getValue(), indent + 2));
            }
            builder.append("\n").append(" ".repeat(indent)).append("}");
            return builder.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            builder.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(",\n");
                }
                builder.append(" ".repeat(indent + 2)).append(toJson(list.get(i), indent + 2));
            }
            builder.append("\n").append(" ".repeat(indent)).append("]");
            return builder.toString();
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
