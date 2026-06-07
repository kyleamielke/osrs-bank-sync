package io.kyil.osrsbanksync;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
class BankSyncButton
{
    private static final String BUTTON_NAME = "osrsbanksync-sync-button";
    private static final String BUTTON_TEXT = "Sync";
    private static final String INVALID_URL_MESSAGE =
        "Bank Sync: target URL must not contain user:pass@ or ?query — refusing to submit until fixed.";

    private final Client client;
    private final ClientThread clientThread;
    private final BankCaptureService captureService;
    private final BankSubmitter submitter;
    private final OsrsBankSyncConfig config;

    @Inject
    BankSyncButton(
        Client client,
        ClientThread clientThread,
        BankCaptureService captureService,
        BankSubmitter submitter,
        OsrsBankSyncConfig config)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.captureService = captureService;
        this.submitter = submitter;
        this.config = config;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() != InterfaceID.BANK)
        {
            return;
        }

        clientThread.invokeLater(this::addButtonIfMissing);
    }

    private void addButtonIfMissing()
    {
        Widget titleBar = client.getWidget(WidgetInfo.BANK_TITLE_BAR);
        Widget searchButton = client.getWidget(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND);
        if (titleBar == null || searchButton == null)
        {
            return;
        }

        if (findButton(titleBar) != null)
        {
            return;
        }

        int width = 40;
        int height = searchButton.getOriginalHeight();
        int x = searchButton.getOriginalX() - width - 4;
        int y = searchButton.getOriginalY();

        Widget button = titleBar.createChild(-1, WidgetType.TEXT)
            .setName(BUTTON_NAME)
            .setText(BUTTON_TEXT)
            .setTextColor(0xFFFFFF)
            .setTextShadowed(true)
            .setFontId(FontID.PLAIN_11)
            .setXTextAlignment(WidgetTextAlignment.CENTER)
            .setYTextAlignment(WidgetTextAlignment.CENTER)
            .setPos(x, y)
            .setSize(width, height)
            .setXPositionMode(searchButton.getXPositionMode())
            .setYPositionMode(searchButton.getYPositionMode())
            .setHasListener(true);

        button.setAction(0, "Sync bank now");
        button.setOnOpListener((JavaScriptCallback) e -> onButtonClick());
        button.revalidate();
    }

    private Widget findButton(Widget parent)
    {
        Widget[] children = parent.getChildren();
        if (children == null)
        {
            return null;
        }

        for (Widget child : children)
        {
            if (child != null && BUTTON_NAME.equals(child.getName()))
            {
                return child;
            }
        }
        return null;
    }

    private void onButtonClick()
    {
        if (config.submitMode() == OsrsBankSyncConfig.SubmitMode.OFF || !config.includeBank())
        {
            return;
        }

        if (!TargetUrlValidator.isValid(config.targetUrl()))
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", INVALID_URL_MESSAGE, null);
            return;
        }

        BankSnapshot snapshot = captureService.captureNow();
        if (snapshot == null)
        {
            log.debug("Sync button clicked but no snapshot was available");
            return;
        }

        submitter.submit(snapshot, true);
    }
}
