package com.jimuqu.solon.claw.llm.dialect;

/** Formats upstream model responses for diagnostic logs. */
final class RawResponseLogSupport {
    private static final int MAX_LOG_BODY_LENGTH = 16000;
    private static final int MAX_HEX_BYTES = 64;

    private RawResponseLogSupport() {}

    static String preview(String body) {
        if (body == null) {
            return "<null>";
        }
        String text =
                body.length() <= MAX_LOG_BODY_LENGTH
                        ? body
                        : body.substring(0, MAX_LOG_BODY_LENGTH)
                                + "\n...[truncated, totalLength="
                                + body.length()
                                + "]";
        return escapeControls(text);
    }

    static String hexHead(String body) {
        if (body == null) {
            return "";
        }
        int limit = Math.min(body.length(), MAX_HEX_BYTES);
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (buffer.length() > 0) {
                buffer.append(' ');
            }
            int value = body.charAt(i) & 0xff;
            if (value < 16) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(value).toUpperCase(java.util.Locale.ROOT));
        }
        if (body.length() > limit) {
            buffer.append(" ...");
        }
        return buffer.toString();
    }

    private static String escapeControls(String text) {
        StringBuilder buffer = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                buffer.append("\\n");
            } else if (ch == '\r') {
                buffer.append("\\r");
            } else if (ch == '\t') {
                buffer.append("\\t");
            } else if (ch < 32 || ch == 127) {
                buffer.append("\\u");
                String hex = Integer.toHexString(ch).toUpperCase(java.util.Locale.ROOT);
                for (int j = hex.length(); j < 4; j++) {
                    buffer.append('0');
                }
                buffer.append(hex);
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }
}
