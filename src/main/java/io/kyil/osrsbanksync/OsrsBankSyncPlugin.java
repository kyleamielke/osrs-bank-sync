package io.kyil.osrsbanksync;

import com.google.inject.Provides;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
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
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final String CONFIG_GROUP = "osrsbanksync";
    private static final String TARGET_URL_KEY = "targetUrl";
    private static final String INVALID_URL_MESSAGE =
        "Bank Sync: target URL must not contain user:pass@ or ?query — refusing to submit until fixed.";
    private static final String PLAINTEXT_WARNING_MESSAGE =
        "Bank sync is sending data over plaintext HTTP to a non-loopback host.";

    private volatile BankSnapshot lastCapturedSnapshot;
    private volatile boolean configValid = true;
    private final AtomicReference<String> lastChatWarnedPlaintextUrl = new AtomicReference<>();

    @Inject
    private Client client;

    @Inject
    private OsrsBankSyncConfig config;

    @Inject
    private BankCaptureService captureService;

    @Inject
    private BankSubmitter submitter;

    @Inject
    private BankSyncButton bankSyncButton;

    @Inject
    private EventBus eventBus;

    @Override
    protected void startUp() throws Exception
    {
        dirty.set(false);
        lastCapturedSnapshot = null;
        lastChatWarnedPlaintextUrl.set(null);
        configValid = validateTargetUrl(config.targetUrl());
        eventBus.register(bankSyncButton);
        if (!configValid)
        {
            log.warn(INVALID_URL_MESSAGE);
        }
        log.info("OSRS Bank Sync started");
    }

    @Override
    protected void shutDown() throws Exception
    {
        dirty.set(false);
        lastCapturedSnapshot = null;
        lastChatWarnedPlaintextUrl.set(null);
        eventBus.unregister(bankSyncButton);
        log.info("OSRS Bank Sync stopped");
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.BANK.getId())
        {
            return;
        }

        lastCapturedSnapshot = captureService.captureFrom(event.getItemContainer());
        dirty.set(true);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() != InterfaceID.BANK || !event.isUnload())
        {
            return;
        }

        if (!configValid || !config.includeBank() || config.submitMode() == OsrsBankSyncConfig.SubmitMode.OFF)
        {
            return;
        }

        if (config.submitMode() == OsrsBankSyncConfig.SubmitMode.MANUAL_ONLY)
        {
            return;
        }

        if (!dirty.get())
        {
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN || client.getAccountHash() == -1L)
        {
            return;
        }

        BankSnapshot snapshot = captureService.captureNow();
        if (snapshot == null)
        {
            return;
        }

        lastCapturedSnapshot = snapshot;
        maybeWarnPlaintextNonLoopbackInChat();
        handleSubmitResult(submitter.submit(snapshot, false));
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState gameState = event.getGameState();
        if (gameState != GameState.HOPPING
            && gameState != GameState.CONNECTION_LOST
            && gameState != GameState.LOGIN_SCREEN)
        {
            return;
        }

        if (!configValid || !config.includeBank() || config.submitMode() == OsrsBankSyncConfig.SubmitMode.OFF)
        {
            return;
        }

        if (config.submitMode() == OsrsBankSyncConfig.SubmitMode.MANUAL_ONLY)
        {
            return;
        }

        if (!dirty.get() || lastCapturedSnapshot == null)
        {
            return;
        }

        maybeWarnPlaintextNonLoopbackInChat();
        handleSubmitResult(submitter.submit(lastCapturedSnapshot, false));
    }

    private void handleSubmitResult(SubmitResult result)
    {
        if (result.getOutcome() == SubmitResult.Outcome.SENT_OK
            || result.getOutcome() == SubmitResult.Outcome.SENT_REJECTED_TERMINAL)
        {
            dirty.set(false);
        }

        switch (result.getOutcome())
        {
            case SENT_OK:
                if (config.showChatConfirmations())
                {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Bank synced.", null);
                }
                break;
            case SENT_REJECTED_TERMINAL:
                if (result.getHttpCode() == 401)
                {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "Bank sync auth failed — check token in config.", null);
                }
                else if (config.showChatConfirmations())
                {
                    String chat = "Bank sync rejected: " + StringUtils.truncate(result.getResponseBody(), 80);
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chat, null);
                }
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()) || !TARGET_URL_KEY.equals(event.getKey()))
        {
            return;
        }

        configValid = validateTargetUrl(config.targetUrl());
        if (!configValid)
        {
            log.warn(INVALID_URL_MESSAGE);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", INVALID_URL_MESSAGE, null);
        }
    }

    private boolean validateTargetUrl(String raw)
    {
        return TargetUrlValidator.isValid(raw);
    }

    private void maybeWarnPlaintextNonLoopbackInChat()
    {
        String targetUrl = config.targetUrl();
        if (targetUrl == null)
        {
            return;
        }

        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(targetUrl);
        if (!BankSubmitter.shouldWarnPlaintextNonLoopback(parsed))
        {
            return;
        }

        if (!targetUrl.equals(lastChatWarnedPlaintextUrl.getAndSet(targetUrl)))
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", PLAINTEXT_WARNING_MESSAGE, null);
        }
    }

    @Provides
    OsrsBankSyncConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OsrsBankSyncConfig.class);
    }
}
