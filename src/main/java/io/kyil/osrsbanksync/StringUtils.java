package io.kyil.osrsbanksync;

final class StringUtils
{
    private StringUtils()
    {
    }

    static String truncate(String value, int maxLen)
    {
        if (value == null || value.isEmpty() || maxLen <= 0)
        {
            return "";
        }

        if (value.length() <= maxLen)
        {
            return value;
        }

        return value.substring(0, maxLen);
    }
}
