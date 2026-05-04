package com.jimuqu.solon.claw.skillhub.support;

import java.util.Map;

/** Skills Hub HTTP 抽象。 */
public interface SkillHubHttpClient {
    String getText(String url, Map<String, String> headers) throws Exception;

    byte[] getBytes(String url, Map<String, String> headers) throws Exception;

    String postJson(String url, Map<String, String> headers, String jsonBody) throws Exception;
}
