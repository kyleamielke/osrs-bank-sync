package io.kyil.osrsbanksync;

import net.runelite.api.vars.AccountType;
import org.junit.Assert;
import org.junit.Test;

public class AccountTypeMapperTest
{
    @Test
    public void testMapsAllSupportedTypes()
    {
        Assert.assertEquals("NORMAL", AccountTypeMapper.toWire(AccountType.NORMAL));
        Assert.assertEquals("IRONMAN", AccountTypeMapper.toWire(AccountType.IRONMAN));
        Assert.assertEquals("ULTIMATE_IRONMAN", AccountTypeMapper.toWire(AccountType.ULTIMATE_IRONMAN));
        Assert.assertEquals("HARDCORE_IRONMAN", AccountTypeMapper.toWire(AccountType.HARDCORE_IRONMAN));
        Assert.assertEquals("GROUP_IRONMAN", AccountTypeMapper.toWire(AccountType.GROUP_IRONMAN));
        Assert.assertEquals("HARDCORE_GROUP_IRONMAN", AccountTypeMapper.toWire(AccountType.HARDCORE_GROUP_IRONMAN));
    }

    @Test
    public void testNullInputMapsToNull()
    {
        Assert.assertNull(AccountTypeMapper.toWire(null));
    }
}
