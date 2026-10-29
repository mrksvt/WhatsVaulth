package com.mrksvt.waen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the release cache in {@code UpdateInfoStore}.
 *
 * <p>Deliberately free of {@code org.json} and {@code android.*} so the backward-compatibility
 * rules for older cache entries can be unit tested on the JVM: a cache written before
 * {@code apkUrl}/{@code apkSize} existed must still parse, yielding an empty url and size 0.
 */
public final class ReleaseCacheParser {

    private ReleaseCacheParser() {
    }

    /**
     * Decodes the JSON array of release objects stored in prefs.
     *
     * @return releases in stored order; empty for blank or malformed input
     */
    public static List<ReleaseInfo> parseReleases(String json) {
        List<ReleaseInfo> releases = new ArrayList<>();
        if (json == null) return releases;
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) return releases;

        List<Object> parsed;
        try {
            Object value = new Reader(trimmed).readValue();
            if (!(value instanceof List)) return releases;
            //noinspection unchecked
            parsed = (List<Object>) value;
        } catch (RuntimeException malformed) {
            // Corrupt cache: treat as empty and refetch next launch.
            return releases;
        }

        for (Object item : parsed) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) item;
            releases.add(new ReleaseInfo(
                    string(entry.get("tag")),
                    string(entry.get("title")),
                    string(entry.get("hash")),
                    string(entry.get("changelog")),
                    string(entry.get("url")),
                    string(entry.get("published")),
                    bool(entry.get("prerelease")),
                    string(entry.get("apkUrl")),
                    number(entry.get("apkSize"))));
        }
        return releases;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(string(value));
    }

    private static long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(string(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Minimal JSON reader: objects, arrays, strings, numbers, booleans and null. */
    private static final class Reader {
        private final String source;
        private int pos;

        Reader(String source) {
            this.source = source;
        }

        Object readValue() {
            skipWhitespace();
            if (pos >= source.length()) throw new IllegalArgumentException("unexpected end");
            char c = source.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                if (peek() != ':') throw new IllegalArgumentException("expected ':'");
                pos++;
                result.put(key, readValue());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == '}') return result;
                if (next != ',') throw new IllegalArgumentException("expected ',' or '}'");
            }
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            pos++; // '['
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == ']') return result;
                if (next != ',') throw new IllegalArgumentException("expected ',' or ']'");
            }
        }

        private String readString() {
            if (peek() != '"') throw new IllegalArgumentException("expected string");
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= source.length()) throw new IllegalArgumentException("unterminated string");
                char c = source.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= source.length()) throw new IllegalArgumentException("bad escape");
                char esc = source.charAt(pos++);
                switch (esc) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (pos + 4 > source.length()) throw new IllegalArgumentException("bad unicode");
                        out.append((char) Integer.parseInt(source.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("bad escape");
                }
            }
        }

        private Number readNumber() {
            int start = pos;
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9') || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String raw = source.substring(start, pos);
            if (raw.isEmpty()) throw new IllegalArgumentException("expected value");
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        }

        private void expect(String literal) {
            if (!source.startsWith(literal, pos)) throw new IllegalArgumentException("expected " + literal);
            pos += literal.length();
        }

        private char peek() {
            if (pos >= source.length()) throw new IllegalArgumentException("unexpected end");
            return source.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++;
        }
    }
}
