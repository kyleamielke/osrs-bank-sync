package io.kyil.osrsbanksync;

public final class SubmitResult
{
    public enum Outcome
    {
        SENT_OK,
        SENT_REJECTED_TERMINAL,
        SENT_TRANSIENT_FAIL,
        SKIPPED_DEDUPE,
        NOT_ATTEMPTED
    }

    private final Outcome outcome;
    private final int httpCode;
    private final String responseBody;

    public SubmitResult(Outcome outcome, int httpCode, String responseBody)
    {
        this.outcome = outcome;
        this.httpCode = httpCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public Outcome getOutcome()
    {
        return outcome;
    }

    public int getHttpCode()
    {
        return httpCode;
    }

    public String getResponseBody()
    {
        return responseBody;
    }
}
