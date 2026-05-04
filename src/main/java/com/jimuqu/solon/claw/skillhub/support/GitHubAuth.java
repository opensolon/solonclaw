package com.jimuqu.solon.claw.skillhub.support;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;

/** GitHub API 鉴权辅助。 */
public class GitHubAuth {
    private final SkillHubHttpClient httpClient;

    private String cachedToken;
    private String cachedMethod;
    private long appTokenExpiryAt;

    public GitHubAuth(SkillHubHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/vnd.github.v3+json");
        String token = resolveToken();
        if (StrUtil.isNotBlank(token)) {
            headers.put("Authorization", "token " + token);
        }
        return headers;
    }

    public boolean isAuthenticated() {
        return StrUtil.isNotBlank(resolveToken());
    }

    public String authMethod() {
        resolveToken();
        return StrUtil.blankToDefault(cachedMethod, "anonymous");
    }

    private String resolveToken() {
        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(cachedToken)
                && (!"github-app".equals(cachedMethod) || now < appTokenExpiryAt)) {
            return cachedToken;
        }

        String configToken =
                StrUtil.blankToDefault(
                        RuntimeConfigResolver.getValue("solonclaw.integrations.github.token"),
                        RuntimeConfigResolver.getValue("solonclaw.integrations.github.cliToken"));
        if (StrUtil.isNotBlank(configToken)) {
            cachedToken = configToken.trim();
            cachedMethod = "pat";
            return cachedToken;
        }

        String ghToken = tryGhCli();
        if (StrUtil.isNotBlank(ghToken)) {
            cachedToken = ghToken;
            cachedMethod = "gh-cli";
            return cachedToken;
        }

        String appToken = tryGitHubApp();
        if (StrUtil.isNotBlank(appToken)) {
            cachedToken = appToken;
            cachedMethod = "github-app";
            appTokenExpiryAt = now + 55L * 60L * 1000L;
            return cachedToken;
        }

        cachedMethod = "anonymous";
        return null;
    }

    private String tryGhCli() {
        try {
            Process process =
                    new ProcessBuilder("gh", "auth", "token").redirectErrorStream(true).start();
            byte[] output = IoUtil.readBytes(process.getInputStream());
            process.waitFor();
            if (process.exitValue() == 0) {
                String token = new String(output, StandardCharsets.UTF_8).trim();
                return StrUtil.blankToDefault(token, null);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private String tryGitHubApp() {
        String appId = RuntimeConfigResolver.getValue("solonclaw.integrations.github.appId");
        String keyPath =
                RuntimeConfigResolver.getValue("solonclaw.integrations.github.privateKeyPath");
        String installationId =
                RuntimeConfigResolver.getValue("solonclaw.integrations.github.installationId");
        if (StrUtil.hasBlank(appId, keyPath, installationId)) {
            return null;
        }

        try {
            File keyFile = FileUtil.file(keyPath);
            if (!keyFile.exists()) {
                return null;
            }
            String privateKeyPem = FileUtil.readUtf8String(keyFile);
            String jwt = buildJwt(privateKeyPem, appId);
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put("Authorization", "Bearer " + jwt);
            headers.put("Accept", "application/vnd.github.v3+json");
            String response =
                    httpClient.postJson(
                            "https://api.github.com/app/installations/"
                                    + installationId
                                    + "/access_tokens",
                            headers,
                            "{}");
            return ONode.ofJson(response).get("token").getString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildJwt(String privateKeyPem, String appId) throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String payloadJson =
                "{\"iat\":"
                        + (now - 60L)
                        + ",\"exp\":"
                        + (now + 600L)
                        + ",\"iss\":\""
                        + appId
                        + "\"}";
        String encodedHeader = Base64.encodeUrlSafe(headerJson);
        String encodedPayload = Base64.encodeUrlSafe(payloadJson);
        String message = encodedHeader + "." + encodedPayload;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(loadPrivateKey(privateKeyPem));
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.encodeUrlSafe(signature.sign());
        return message + "." + encodedSignature;
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");
        byte[] decoded = java.util.Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
