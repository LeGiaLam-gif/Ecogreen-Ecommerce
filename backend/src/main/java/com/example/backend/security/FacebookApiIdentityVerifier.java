package com.example.backend.security;

import com.example.backend.exception.FacebookLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Facebook has no signed ID token for the JS-SDK flow, so the server asks Facebook itself:
 * <ol>
 *   <li>{@code GET /debug_token} (with the app access token {@code APP_ID|APP_SECRET}) must say {@code is_valid=true} and
 *       {@code app_id} equal to OUR app id (a token minted for another app is rejected);</li>
 *   <li>{@code GET /me?fields=id,name,email} (with {@code appsecret_proof}) gives the identity; its id must match the
 *       token's {@code user_id}.</li>
 * </ol>
 * Only the access token is accepted from the client. Tokens, the app secret and request URLs are never logged.
 */
@Component
public class FacebookApiIdentityVerifier implements FacebookIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(FacebookApiIdentityVerifier.class);
    private static final String GRAPH = "https://graph.facebook.com";
    private static final String INVALID = "Mã xác thực Facebook không hợp lệ hoặc đã hết hạn.";
    private static final String UNREACHABLE = "Không thể xác thực với Facebook lúc này. Vui lòng thử lại sau.";

    private final String appId;
    private final String appSecret;
    private final String base; // https://graph.facebook.com[/vNN.N]
    private final HttpClient client;

    @Autowired
    public FacebookApiIdentityVerifier(@Value("${ecogreen.facebook.app-id:}") String appId,
                                       @Value("${ecogreen.facebook.app-secret:}") String appSecret,
                                       @Value("${ecogreen.facebook.graph-version:v21.0}") String graphVersion) {
        this(appId, appSecret, GRAPH, graphVersion,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Test seam: a different host (a local HTTP server) and client. */
    FacebookApiIdentityVerifier(String appId, String appSecret, String host, String graphVersion, HttpClient client) {
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        String version = graphVersion == null ? "" : graphVersion.trim();
        this.base = version.isEmpty() ? host : host + "/" + version;
        this.client = client;
    }

    @Override
    public boolean isConfigured() {
        return !appId.isEmpty() && !appSecret.isEmpty();
    }

    @Override
    public FacebookIdentity verify(String accessToken) {
        if (!isConfigured()) {
            throw new FacebookLoginUnavailableException("Đăng nhập bằng Facebook hiện chưa được cấu hình.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new UnauthorizedException(INVALID);
        }

        JsonObject debug = get(base + "/debug_token?input_token=" + enc(accessToken)
                + "&access_token=" + enc(appId + "|" + appSecret), true);
        JsonObject data = obj(debug.get("data"));
        if (data == null || !bool(data.get("is_valid")) || !appId.equals(text(data.get("app_id")))) {
            throw new UnauthorizedException(INVALID);
        }

        JsonObject me = get(base + "/me?fields=id,name,email&access_token=" + enc(accessToken)
                + "&appsecret_proof=" + proof(accessToken), false);
        String id = text(me.get("id"));
        String tokenUser = text(data.get("user_id"));
        if (id == null || id.isBlank() || (tokenUser != null && !tokenUser.equals(id))) {
            throw new UnauthorizedException(INVALID);
        }
        return new FacebookIdentity(id, text(me.get("name")), text(me.get("email")));
    }

    private JsonObject get(String url, boolean appCall) {
        HttpResponse<String> response;
        try {
            response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FacebookLoginUnavailableException(UNREACHABLE);
        } catch (IOException | RuntimeException e) {
            throw new FacebookLoginUnavailableException(UNREACHABLE);
        }
        int status = response.statusCode();
        if (status >= 500) {
            throw new FacebookLoginUnavailableException(UNREACHABLE);
        }
        if (status >= 400) {
            if (appCall) {
                log.warn("Facebook debug_token answered HTTP {}: the token is bad, or FACEBOOK_APP_ID / FACEBOOK_APP_SECRET are wrong", status);
            }
            throw new UnauthorizedException(INVALID);
        }
        try {
            JsonObject json = obj(JsonParser.parseString(response.body()));
            if (json == null) throw new FacebookLoginUnavailableException(UNREACHABLE);
            return json;
        } catch (RuntimeException e) {
            if (e instanceof FacebookLoginUnavailableException f) throw f;
            throw new FacebookLoginUnavailableException(UNREACHABLE);
        }
    }

    /** appsecret_proof = HMAC-SHA256(access token, key = app secret), hex. Required when the app enforces it, harmless otherwise. */
    private String proof(String accessToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonObject obj(JsonElement e) {
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static boolean bool(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static String text(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
