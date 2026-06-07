package io.kyil.osrsbanksync;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class BankSubmitter
{
    private static final String USER_AGENT = "osrs-bank-sync/0.1.0 (+https://github.com/kyleamielke/osrs-bank-sync)";
    private static final int LOG_RESPONSE_BODY_LIMIT = 200;
    private static final MediaType JSON_UTF8 = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson wireGson;
    private final OsrsBankSyncConfig config;
    private final AtomicReference<String> lastWarnedPlaintextUrl = new AtomicReference<>();
    private String lastSubmittedPayloadHash;

    @Inject
    public BankSubmitter(OkHttpClient okHttpClient, Gson gson, OsrsBankSyncConfig config)
    {
        this.httpClient = okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();
        this.wireGson = gson.newBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .serializeNulls()
            .create();
        this.config = config;
    }

    public SubmitResult submit(BankSnapshot snapshot, boolean force)
    {
        if (snapshot == null)
        {
            return new SubmitResult(SubmitResult.Outcome.NOT_ATTEMPTED, 0, "");
        }

        String baseUrl = config.targetUrl();
        HttpUrl target = buildTargetUrl(baseUrl);
        if (target == null)
        {
            return new SubmitResult(SubmitResult.Outcome.NOT_ATTEMPTED, 0, "");
        }

        if (shouldWarnPlaintextNonLoopback(target) && baseUrl != null
            && !baseUrl.equals(lastWarnedPlaintextUrl.getAndSet(baseUrl)))
        {
            log.warn("Bank sync is sending data over plaintext HTTP to a non-loopback host: {}", target.host());
            // TODO(4.3): emit the same warning to chat once per URL.
        }

        String payloadHash = payloadHashForDedupe(snapshot);
        if (!force && payloadHash.equals(lastSubmittedPayloadHash))
        {
            return new SubmitResult(SubmitResult.Outcome.SKIPPED_DEDUPE, 0, "");
        }

        String payloadJson = wireGson.toJson(snapshot);
        Request.Builder requestBuilder = new Request.Builder()
            .url(target)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", USER_AGENT)
            .post(RequestBody.create(JSON_UTF8, payloadJson));
        String token = config.authToken();
        if (token != null && !token.isEmpty())
        {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        Request request = requestBuilder.build();
        log.debug("Sending bank sync request with headers: {}", request.headers().names());

        try (Response response = httpClient.newCall(request).execute())
        {
            int code = response.code();
            String responseBody = readTruncatedBody(response.body());
            log.info("Bank submit response status={} body={}", code, responseBody);

            if (code == 200 || code == 204)
            {
                lastSubmittedPayloadHash = payloadHash;
                return new SubmitResult(SubmitResult.Outcome.SENT_OK, code, "");
            }
            if (code >= 400 && code <= 499)
            {
                return new SubmitResult(SubmitResult.Outcome.SENT_REJECTED_TERMINAL, code, responseBody);
            }
            if (code >= 500 && code <= 599)
            {
                return new SubmitResult(SubmitResult.Outcome.SENT_TRANSIENT_FAIL, code, "");
            }
            return new SubmitResult(SubmitResult.Outcome.SENT_TRANSIENT_FAIL, code, "");
        }
        catch (IOException e)
        {
            log.warn("Bank submit failed with IO exception", e);
            return new SubmitResult(SubmitResult.Outcome.SENT_TRANSIENT_FAIL, 0, "");
        }
    }

    String payloadHashForDedupe(BankSnapshot snapshot)
    {
        JsonObject payload = wireGson.toJsonTree(snapshot).getAsJsonObject();
        payload.remove("snapshot_id");
        payload.remove("captured_at");
        return sha256Hex(wireGson.toJson(payload));
    }

    static boolean shouldWarnPlaintextNonLoopback(HttpUrl url)
    {
        if (url == null || !"http".equals(url.scheme()))
        {
            return false;
        }

        String host = url.host();
        return !"127.0.0.1".equals(host) && !"localhost".equals(host) && !"::1".equals(host);
    }

    private static HttpUrl buildTargetUrl(String baseUrl)
    {
        if (baseUrl == null)
        {
            return null;
        }

        HttpUrl base = HttpUrl.parse(baseUrl);
        if (base == null)
        {
            return null;
        }

        return base.newBuilder()
            .addPathSegments("api/v1/sync/bank")
            .build();
    }

    private static String readTruncatedBody(ResponseBody responseBody) throws IOException
    {
        if (responseBody == null)
        {
            return "";
        }

        String body = responseBody.string();
        return StringUtils.truncate(body, LOG_RESPONSE_BODY_LIMIT);
    }

    private static String sha256Hex(String value)
    {
        MessageDigest digest = sha256Digest();
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    private static MessageDigest sha256Digest()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            out.append(Character.forDigit((value >> 4) & 0xF, 16));
            out.append(Character.forDigit(value & 0xF, 16));
        }
        return out.toString();
    }
}
