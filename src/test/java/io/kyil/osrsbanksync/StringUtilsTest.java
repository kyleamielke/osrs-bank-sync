package io.kyil.osrsbanksync;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilsTest
{
    @Test
    public void testTruncateEmpty()
    {
        Assert.assertEquals("", StringUtils.truncate("", 80));
    }

    @Test
    public void testTruncateShorterThanMax()
    {
        Assert.assertEquals("abc", StringUtils.truncate("abc", 80));
    }

    @Test
    public void testTruncateExactlyMax()
    {
        Assert.assertEquals("abcd", StringUtils.truncate("abcd", 4));
    }

    @Test
    public void testTruncateLongerThanMax()
    {
        Assert.assertEquals("abc", StringUtils.truncate("abcdef", 3));
    }
}
