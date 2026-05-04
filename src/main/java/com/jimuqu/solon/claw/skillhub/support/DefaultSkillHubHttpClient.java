package com.jimuqu.solon.claw.skillhub.support;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 默认 HTTP 客户端。 */
public class DefaultSkillHubHttpClient implements SkillHubHttpClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

    @Override
    public String getText(String url, Map<String, String> headers) throws Exception {
        Response response = executeGet(url, headers);
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code() + " for " + url);
            }
            return response.body() == null ? "" : response.body().string();
        } finally {
            response.close();
        }
    }

    @Override
    public byte[] getBytes(String url, Map<String, String> headers) throws Exception {
        Response response = executeGet(url, headers);
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code() + " for " + url);
            }
            return response.body() == null ? new byte[0] : response.body().bytes();
        } finally {
            response.close();
        }
    }

    @Override
    public String postJson(String url, Map<String, String> headers, String jsonBody)
            throws Exception {
        RequestBody body = RequestBody.create(jsonBody == null ? "{}" : jsonBody, JSON);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        for (Map.Entry<String, String> entry : safeHeaders(headers).entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        Response response = client.newCall(builder.build()).execute();
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code() + " for " + url);
            }
            return response.body() == null ? "" : response.body().string();
        } finally {
            response.close();
        }
    }

    private Response executeGet(String url, Map<String, String> headers) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> entry : safeHeaders(headers).entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return client.newCall(builder.build()).execute();
    }

    private Map<String, String> safeHeaders(Map<String, String> headers) {
        return headers == null ? Collections.<String, String>emptyMap() : headers;
    }
}
