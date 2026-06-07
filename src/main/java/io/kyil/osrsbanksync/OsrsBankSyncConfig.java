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
}
