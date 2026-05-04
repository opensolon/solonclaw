package com.jimuqu.solon.claw.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Bounded attachment/update IO helpers. */
public final class BoundedAttachmentIO {
    public static final long DEFAULT_MAX_BYTES = 32L * 1024L * 1024L;
    public static final long UPDATE_JAR_MAX_BYTES = 200L * 1024L * 1024L;
    public static final long JSON_MAX_BYTES = 1024L * 1024L;

    private BoundedAttachmentIO() {}

    public static byte[] downloadHutool(String url, int timeoutMillis, long maxBytes) {
        HttpResponse response = HttpRequest.get(url).timeout(timeoutMillis).executeAsync();
        try {
            if (response.getStatus() >= 400) {
                throw new IllegalStateException("Download failed, HTTP " + response.getStatus());
            }
            return readHutoolResponse(response, maxBytes);
        } finally {
            response.close();
        }
    }

    public static void downloadHutoolToFile(
            String url, File target, int timeoutMillis, long maxBytes) {
        byte[] data = downloadHutool(url, timeoutMillis, maxBytes);
        FileUtil.mkParentDirs(target);
        FileUtil.writeBytes(data, target);
    }

    public static String readHutoolText(HttpResponse response, long maxBytes) {
        return new String(readHutoolResponse(response, maxBytes), StandardCharsets.UTF_8);
    }

    public static byte[] readHutoolResponse(HttpResponse response, long maxBytes) {
        String lengthHeader = response.header("Content-Length");
        checkContentLength(lengthHeader, maxBytes);
        InputStream stream = response.bodyStream();
        if (stream == null) {
            return new byte[0];
        }
        return readLimited(stream, maxBytes);
    }

    public static byte[] readOkHttpResponse(Response response, long maxBytes) throws Exception {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IllegalStateException("Download body is empty");
        }
        long contentLength = body.contentLength();
        if (contentLength > maxBytes) {
            throw new IllegalStateException("Download exceeds max size: " + contentLength);
        }
        return readLimited(body.byteStream(), maxBytes);
    }

    private static void checkContentLength(String value, long maxBytes) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        try {
            long length = Long.parseLong(value.trim());
            if (length > maxBytes) {
                throw new IllegalStateException("Download exceeds max size: " + length);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static byte[] readLimited(InputStream stream, long maxBytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException("Download exceeds max size: " + total);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read bounded stream: " + e.getMessage(), e);
        }
    }
}
