package io.github.qdtranslate.provider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.github.qdtranslate.core.Http;

/**
 * GigaChat API (Сбер). Авторизация: «ключ авторизации» (Base64 client_id:secret) → OAuth-токен на 30 минут.
 * Серверы Сбера подписаны корневым сертификатом Минцифры, которого нет в Android, —
 * поэтому сертификат передаётся в PEM из настроек и добавляется к системным (проверка TLS не отключается).
 */
final class GigaChat extends LlmTranslator {
    private static final String OAUTH = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";

    private final String base, authKey, model, scope;
    private final SSLSocketFactory ssl;

    private static String token;
    private static long tokenExpires;

    GigaChat(String base, String authKey, String model, String scope, String pem, double temperature) throws Exception {
        super(temperature);
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.authKey = authKey.trim();
        this.model = model;
        this.scope = scope;
        this.ssl = pem == null || pem.trim().isEmpty() ? null : sslWith(pem);
    }

    private synchronized String token() throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpires - 60000) return token;
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Basic " + authKey);
        h.put("RqUID", UUID.randomUUID().toString());
        h.put("Content-Type", "application/x-www-form-urlencoded");
        JSONObject j = new JSONObject(Http.request("POST", OAUTH, h, "scope=" + scope, ssl, 30000));
        token = j.getString("access_token");
        tokenExpires = j.optLong("expires_at", System.currentTimeMillis() + 25 * 60000L);
        return token;
    }

    private Map<String, String> headers() throws Exception {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token());
        h.put("Content-Type", "application/json");
        return h;
    }

    @Override
    String chat(String system, String user) throws Exception {
        JSONObject body = new JSONObject()
            .put("model", model)
            .put("temperature", Math.max(0.01, temperature))
            .put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(new JSONObject().put("role", "user").put("content", user)));
        JSONObject j = new JSONObject(Http.request("POST", base + "/chat/completions", headers(), body.toString(), ssl, 180000));
        return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
    }

    @Override
    public List<String> listModels() throws Exception {
        JSONObject j = new JSONObject(Http.request("GET", base + "/models", headers(), null, ssl, 30000));
        JSONArray d = j.getJSONArray("data");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < d.length(); i++) out.add(d.getJSONObject(i).getString("id"));
        return out;
    }

    /** TrustManager = системные CA + сертификаты из PEM. */
    private static SSLSocketFactory sslWith(String pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        int i = 0;
        for (Certificate c : cf.generateCertificates(new ByteArrayInputStream(pem.trim().getBytes(StandardCharsets.US_ASCII)))) {
            ks.setCertificateEntry("extra" + (i++), c);
        }
        final X509TrustManager extra = firstX509(ks);
        final X509TrustManager system = firstX509(null);
        X509TrustManager both = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                system.checkClientTrusted(chain, auth);
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                try { system.checkServerTrusted(chain, auth); }
                catch (CertificateException e) { extra.checkServerTrusted(chain, auth); }
            }
            @Override public X509Certificate[] getAcceptedIssuers() { return system.getAcceptedIssuers(); }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{both}, null);
        return ctx.getSocketFactory();
    }

    private static X509TrustManager firstX509(KeyStore ks) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        throw new IllegalStateException("no X509TrustManager");
    }
}
