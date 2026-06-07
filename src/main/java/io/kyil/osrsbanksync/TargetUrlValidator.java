package io.kyil.osrsbanksync;

import okhttp3.HttpUrl;

final class TargetUrlValidator
{
    private TargetUrlValidator()
    {
    }

    static boolean isValid(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return false;
        }

        HttpUrl url = HttpUrl.parse(raw);
        if (url == null)
        {
            return false;
        }

        if (!url.username().isEmpty())
        {
            return false;
        }

        if (!url.password().isEmpty())
        {
            return false;
        }

        return url.querySize() == 0;
    }
}
