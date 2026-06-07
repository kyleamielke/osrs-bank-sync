package io.kyil.osrsbanksync;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;

@Singleton
public class BankCaptureService
{
    static final String PLUGIN_VERSION = "0.1.0-SNAPSHOT";

    private final Client client;

    @Inject
    public BankCaptureService(Client client)
    {
        this.client = client;
    }

    public BankSnapshot captureNow()
    {
        ItemContainer container = client.getItemContainer(InventoryID.BANK);
        if (container == null)
        {
            return null;
        }

        return captureFrom(container);
    }

    public BankSnapshot captureFrom(ItemContainer container)
    {
        List<BankItem> capturedItems = new ArrayList<>();
        Item[] items = container.getItems();
        if (items != null)
        {
            for (int slot = 0; slot < items.length; slot++)
            {
                Item item = items[slot];
                if (item == null)
                {
                    continue;
                }

                int itemId = item.getId();
                int quantity = item.getQuantity();
                if (itemId <= 0 || quantity == 0)
                {
                    continue;
                }

                capturedItems.add(new BankItem(slot, itemId, quantity));
            }
        }

        Player localPlayer = client.getLocalPlayer();
        String displayName = localPlayer != null ? localPlayer.getName() : null;

        return new BankSnapshot(
            client.getAccountHash(),
            displayName,
            AccountTypeMapper.toWire(client.getAccountType()),
            Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            PLUGIN_VERSION,
            UUID.randomUUID().toString(),
            Collections.unmodifiableList(capturedItems)
        );
    }
}
