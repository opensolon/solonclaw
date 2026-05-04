package com.jimuqu.solon.claw.support;

import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

/** 固定响应的 Hub HTTP client。 */
public class FixtureSkillHubHttpClient implements SkillHubHttpClient {
    private final Map<String, String> textResponses = new LinkedHashMap<String, String>();
    private final Map<String, byte[]> byteResponses = new LinkedHashMap<String, byte[]>();

    public FixtureSkillHubHttpClient onText(String url, String body) {
        textResponses.put(url, body);
        return this;
    }

    public FixtureSkillHubHttpClient onBytes(String url, byte[] body) {
        byteResponses.put(url, body);
        return this;
    }

    @Override
    public String getText(String url, Map<String, String> headers) {
        if (!textResponses.containsKey(url)) {
            throw new IllegalStateException("No fixture for url: " + url);
        }
        return textResponses.get(url);
    }

    @Override
    public byte[] getBytes(String url, Map<String, String> headers) {
        if (!byteResponses.containsKey(url)) {
            throw new IllegalStateException("No fixture bytes for url: " + url);
        }
        return byteResponses.get(url);
    }

    @Override
    public String postJson(String url, Map<String, String> headers, String jsonBody) {
        return getText(url, headers);
    }
}
