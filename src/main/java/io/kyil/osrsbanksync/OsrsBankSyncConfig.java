package io.kyil.osrsbanksync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("osrsbanksync")
public interface OsrsBankSyncConfig extends Config
{
    @ConfigItem(
        keyName = "targetUrl",
        name = "Target URL",
        position = 0,
        description = "Base URL of the receiver. POSTs go to {targetUrl}/api/v1/sync/bank."
    )
    default String targetUrl()
    {
        return "http://127.0.0.1:8484";
    }

    @ConfigItem(
        keyName = "authToken",
        name = "Auth token",
        position = 1,
        secret = true,
        description = "Sent as Authorization: Bearer <token>. Leave empty to disable. "
            + "Token is opaque — static key, OAuth, or JWT all work."
    )
    default String authToken()
    {
        return "";
    }

    @ConfigItem(
        keyName = "submitMode",
        name = "Submit mode",
        position = 2,
        description = "When to submit bank snapshots."
    )
    default SubmitMode submitMode()
    {
        return SubmitMode.AUTO_ON_CLOSE;
    }

    @ConfigItem(
        keyName = "includeBank",
        name = "Sync bank",
        position = 3,
        description = "Capture and submit bank contents. Reserved for future include flags."
    )
    default boolean includeBank()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showChatConfirmations",
        name = "Show chat confirmations",
        position = 4,
        description = "Print a chat message on each successful submission."
    )
    default boolean showChatConfirmations()
    {
        return true;
    }

    enum SubmitMode
    {
        AUTO_ON_CLOSE,
        MANUAL_ONLY,
        OFF
    }
}
