package io.kyil.osrsbanksync;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
    name = "OSRS Bank Sync",
    description = "Sync your bank contents to a self-hosted endpoint (e.g. osrs-tracker)",
    tags = {"bank", "sync", "tracker", "wealth"}
)
public class OsrsBankSyncPlugin extends Plugin
{
    @Inject
    private OsrsBankSyncConfig config;

    @Override
    protected void startUp() throws Exception
    {
        log.info("OSRS Bank Sync started");
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("OSRS Bank Sync stopped");
    }

    @Provides
    OsrsBankSyncConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OsrsBankSyncConfig.class);
    }
}
