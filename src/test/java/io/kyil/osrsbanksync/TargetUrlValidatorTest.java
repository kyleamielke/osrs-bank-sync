package io.kyil.osrsbanksync;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetUrlValidatorTest
{
    @Test
    public void acceptsLoopbackHttp()
    {
        assertTrue(TargetUrlValidator.isValid("http://127.0.0.1:8484"));
    }

    @Test
    public void acceptsHttpsWithPort()
    {
        assertTrue(TargetUrlValidator.isValid("https://example.com:8484/some/prefix"));
    }

    @Test
    public void rejectsNull()
    {
        assertFalse(TargetUrlValidator.isValid(null));
    }

    @Test
    public void rejectsEmpty()
    {
        assertFalse(TargetUrlValidator.isValid(""));
    }

    @Test
    public void rejectsBareString()
    {
        assertFalse(TargetUrlValidator.isValid("not a url"));
    }

    @Test
    public void rejectsUserinfo()
    {
        assertFalse(TargetUrlValidator.isValid("http://user:pass@host.example/"));
    }

    @Test
    public void rejectsUsernameOnly()
    {
        assertFalse(TargetUrlValidator.isValid("http://user@host.example/"));
    }

    @Test
    public void rejectsQueryString()
    {
        assertFalse(TargetUrlValidator.isValid("http://host.example/?foo=bar"));
    }

    @Test
    public void acceptsHostWithFragment()
    {
        assertTrue(TargetUrlValidator.isValid("http://host.example/#anchor"));
    }
}
