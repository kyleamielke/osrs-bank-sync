package io.kyil.osrsbanksync;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BankSubmitterTest
{
    private MockWebServer server;
    private OsrsBankSyncConfig config;
    private BankSubmitter submitter;

    @Before
    public void setUp() throws IOException
    {
        server = new MockWebServer();
        server.start();

        config = mock(OsrsBankSyncConfig.class);
        when(config.targetUrl()).thenReturn(server.url("/").toString());
        when(config.authToken()).thenReturn("");

        submitter = new BankSubmitter(new OkHttpClient(), new Gson(), config);
    }

    @After
    public void tearDown() throws IOException
    {
        server.shutdown();
    }

    @Test
    public void testSubmit200ReturnsSentOkAndBuildsExpectedRequest() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        SubmitResult result = submitter.submit(snapshot("id-1", "2025-01-15T22:31:04Z", 100), false);

        Assert.assertEquals(SubmitResult.Outcome.SENT_OK, result.getOutcome());

        RecordedRequest request = server.takeRequest();
        Assert.assertEquals("POST", request.getMethod());
        Assert.assertEquals("/api/v1/sync/bank", request.getPath());
        Assert.assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
        Assert.assertNull(request.getHeader("Authorization"));
    }

    @Test
    public void testSubmit401ReturnsTerminalRejection()
    {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));

        SubmitResult outcome = submitter.submit(snapshot("id-1", "2025-01-15T22:31:04Z", 100), false);

        Assert.assertEquals(SubmitResult.Outcome.SENT_REJECTED_TERMINAL, outcome.getOutcome());
    }

    @Test
    public void testDedupeSkipsWhenOnlySnapshotMetadataChanges() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));

        SubmitResult first = submitter.submit(snapshot("id-1", "2025-01-15T22:31:04Z", 100), false);
        SubmitResult second = submitter.submit(snapshot("id-2", "2025-01-15T22:31:10Z", 100), false);

        Assert.assertEquals(SubmitResult.Outcome.SENT_OK, first.getOutcome());
        Assert.assertEquals(SubmitResult.Outcome.SKIPPED_DEDUPE, second.getOutcome());
        Assert.assertEquals(1, server.getRequestCount());
    }

    @Test
    public void testForceTrueBypassesDedupe() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse().setResponseCode(200));

        SubmitResult first = submitter.submit(snapshot("id-1", "2025-01-15T22:31:04Z", 100), false);
        SubmitResult second = submitter.submit(snapshot("id-2", "2025-01-15T22:31:10Z", 100), true);

        Assert.assertEquals(SubmitResult.Outcome.SENT_OK, first.getOutcome());
        Assert.assertEquals(SubmitResult.Outcome.SENT_OK, second.getOutcome());
        Assert.assertEquals(2, server.getRequestCount());
    }

    @Test
    public void testPayloadHashIgnoresMetadataButChangesForItemQuantity()
    {
        String hashA = submitter.payloadHashForDedupe(snapshot("id-1", "2025-01-15T22:31:04Z", 100));
        String hashB = submitter.payloadHashForDedupe(snapshot("id-2", "2025-01-15T22:31:10Z", 100));
        String hashC = submitter.payloadHashForDedupe(snapshot("id-2", "2025-01-15T22:31:10Z", 101));

        Assert.assertEquals(hashA, hashB);
        Assert.assertNotEquals(hashA, hashC);
    }

    @Test
    public void testAddsAuthorizationHeaderWhenTokenPresent() throws Exception
    {
        when(config.authToken()).thenReturn("my-secret-token");
        server.enqueue(new MockResponse().setResponseCode(200));

        SubmitResult outcome = submitter.submit(snapshot("id-1", "2025-01-15T22:31:04Z", 100), false);

        Assert.assertEquals(SubmitResult.Outcome.SENT_OK, outcome.getOutcome());
        RecordedRequest request = server.takeRequest();
        Assert.assertEquals("Bearer my-secret-token", request.getHeader("Authorization"));
    }

    @Test
    public void testShouldWarnPlaintextNonLoopback()
    {
        Assert.assertTrue(BankSubmitter.shouldWarnPlaintextNonLoopback(HttpUrl.parse("http://example.com:8484")));
        Assert.assertFalse(BankSubmitter.shouldWarnPlaintextNonLoopback(HttpUrl.parse("https://example.com:8484")));
        Assert.assertFalse(BankSubmitter.shouldWarnPlaintextNonLoopback(HttpUrl.parse("http://localhost:8484")));
        Assert.assertFalse(BankSubmitter.shouldWarnPlaintextNonLoopback(HttpUrl.parse("http://127.0.0.1:8484")));
        Assert.assertFalse(BankSubmitter.shouldWarnPlaintextNonLoopback(HttpUrl.parse("http://[::1]:8484")));
    }

    @Test
    public void testRejectedResultIncludesTruncatedBody()
    {
        String body = repeat("x", 240);
        server.enqueue(new MockResponse().setResponseCode(400).setBody(body));

        SubmitResult result = submitter.submit(snapshot("id-1", "2025-01-15T22:31:04Z", 100), false);

        Assert.assertEquals(SubmitResult.Outcome.SENT_REJECTED_TERMINAL, result.getOutcome());
        Assert.assertEquals(400, result.getHttpCode());
        Assert.assertEquals(200, result.getResponseBody().length());
    }

    private static BankSnapshot snapshot(String snapshotId, String capturedAt, int quantity)
    {
        return new BankSnapshot(
            123L,
            "Zezima",
            "IRONMAN",
            capturedAt,
            "0.1.0-SNAPSHOT",
            snapshotId,
            Collections.singletonList(new BankItem(0, 995, quantity))
        );
    }

    private static String repeat(String value, int count)
    {
        StringBuilder out = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++)
        {
            out.append(value);
        }
        return out.toString();
    }
}
