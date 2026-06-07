package io.kyil.osrsbanksync;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String USER_AGENT = "osrs-bank-sync/0.1.0-SNAPSHOT (+https://github.com/kyleamielke/osrs-bank-sync)";
    private static final int LOG_RESPONSE_BODY_LIMIT = 200;
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final Gson wireGson;
    private final OsrsBankSyncConfig config;
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

    public SubmitOutcome submit(BankSnapshot snapshot, boolean force)
    {
        if (snapshot == null)
        {
            return SubmitOutcome.NOT_ATTEMPTED;
        }

        HttpUrl target = buildTargetUrl(config.targetUrl());
        if (target == null)
        {
            return SubmitOutcome.NOT_ATTEMPTED;
        }

        String payloadHash = payloadHashForDedupe(snapshot);
        if (!force && payloadHash.equals(lastSubmittedPayloadHash))
        {
            return SubmitOutcome.SKIPPED_DEDUPE;
        }

        String payloadJson = wireGson.toJson(snapshot);
        Request request = new Request.Builder()
            .url(target)
            .header("User-Agent", USER_AGENT)
            .post(RequestBody.create(JSON, payloadJson))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            int code = response.code();
            String responseBody = readTruncatedBody(response.body());
            log.info("Bank submit response status={} body={}", code, responseBody);

            if (code == 200 || code == 204)
            {
                lastSubmittedPayloadHash = payloadHash;
                return SubmitOutcome.SENT_OK;
            }
            if (code >= 400 && code <= 499)
            {
                return SubmitOutcome.SENT_REJECTED_TERMINAL;
            }
            if (code >= 500 && code <= 599)
            {
                return SubmitOutcome.SENT_TRANSIENT_FAIL;
            }
            return SubmitOutcome.SENT_TRANSIENT_FAIL;
        }
        catch (IOException e)
        {
            log.warn("Bank submit failed with IO exception", e);
            return SubmitOutcome.SENT_TRANSIENT_FAIL;
        }
    }

    String payloadHashForDedupe(BankSnapshot snapshot)
    {
        JsonObject payload = wireGson.toJsonTree(snapshot).getAsJsonObject();
        payload.remove("snapshot_id");
        payload.remove("captured_at");
        return sha256Hex(wireGson.toJson(payload));
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
        if (body.length() <= LOG_RESPONSE_BODY_LIMIT)
        {
            return body;
        }
        return body.substring(0, LOG_RESPONSE_BODY_LIMIT);
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

    public enum SubmitOutcome
    {
        NOT_ATTEMPTED,
        SKIPPED_DEDUPE,
        SENT_OK,
        SENT_REJECTED_TERMINAL,
        SENT_TRANSIENT_FAIL
    }
}
