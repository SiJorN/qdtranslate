package io.github.qdtranslate.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/** Минимальный HTTP-клиент на HttpURLConnection с повторами при 429/5xx. */
public final class Http {
    private Http() {}

    public static class HttpException extends IOException {
        public final int code;
        public HttpException(int code, String body) {
            super("HTTP " + code + ": " + (body.length() > 400 ? body.substring(0, 400) : body));
            this.code = code;
        }
    }

    public static String request(String method, String url, Map<String, String> headers, String body,
                                 SSLSocketFactory ssl, int timeoutMs) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                if (ssl != null && c instanceof HttpsURLConnection) ((HttpsURLConnection) c).setSSLSocketFactory(ssl);
                c.setRequestMethod(method);
                c.setConnectTimeout(15000);
                c.setReadTimeout(timeoutMs);
                c.setRequestProperty("Accept", "application/json");
                if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
                if (body != null) {
                    c.setDoOutput(true);
                    byte[] b = body.getBytes(StandardCharsets.UTF_8);
                    c.setFixedLengthStreamingMode(b.length);
                    try (OutputStream os = c.getOutputStream()) { os.write(b); }
                }
                int code = c.getResponseCode();
                InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String resp = is == null ? "" : readAll(is);
                if (code == 429 || code >= 500) {
                    last = new HttpException(code, resp);
                    long wait = (long) Math.pow(2, attempt) * 2000L;
                    String ra = c.getHeaderField("Retry-After");
                    if (ra != null) try { wait = Math.min(60000L, Long.parseLong(ra.trim()) * 1000L); } catch (Exception ignored) {}
                    sleep(wait);
                    continue;
                }
                if (code >= 400) throw new HttpException(code, resp);
                return resp;
            } catch (HttpException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                sleep(1500L * (attempt + 1));
            } finally {
                c.disconnect();
            }
        }
        throw last != null ? last : new IOException("request failed");
    }

    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
