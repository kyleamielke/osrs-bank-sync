package io.kyil.osrsbanksync;

import net.runelite.api.vars.AccountType;

final class AccountTypeMapper
{
    private AccountTypeMapper()
    {
    }

    /** Returns the wire string, or null if the input is null. */
    static String toWire(AccountType type)
    {
        if (type == null)
        {
            return null;
        }

        switch (type)
        {
            case NORMAL:
                return "NORMAL";
            case IRONMAN:
                return "IRONMAN";
            case ULTIMATE_IRONMAN:
                return "ULTIMATE_IRONMAN";
            case HARDCORE_IRONMAN:
                return "HARDCORE_IRONMAN";
            case GROUP_IRONMAN:
                return "GROUP_IRONMAN";
            case HARDCORE_GROUP_IRONMAN:
                return "HARDCORE_GROUP_IRONMAN";
            default:
                return null; // future-proof against new enum values
        }
    }
}
