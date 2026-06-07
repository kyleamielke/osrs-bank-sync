package io.kyil.osrsbanksync;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.vars.AccountType;
import org.junit.Assert;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BankCaptureServiceTest
{
    @Test
    public void testCaptureNowReturnsNullWhenBankContainerUnavailable()
    {
        Client client = mock(Client.class);
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(null);

        BankCaptureService service = new BankCaptureService(client);
        Assert.assertNull(service.captureNow());
    }

    @Test
    public void testCaptureFromFiltersAndBuildsSnapshot()
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        ItemContainer container = mock(ItemContainer.class);

        Item keepFirst = mock(Item.class);
        when(keepFirst.getId()).thenReturn(995);
        when(keepFirst.getQuantity()).thenReturn(100);

        Item filteredById = mock(Item.class);
        when(filteredById.getId()).thenReturn(0);
        when(filteredById.getQuantity()).thenReturn(5);

        Item filteredByQuantity = mock(Item.class);
        when(filteredByQuantity.getId()).thenReturn(4151);
        when(filteredByQuantity.getQuantity()).thenReturn(0);

        Item keepSecond = mock(Item.class);
        when(keepSecond.getId()).thenReturn(11840);
        when(keepSecond.getQuantity()).thenReturn(7);

        when(container.getItems()).thenReturn(new Item[]{keepFirst, filteredById, filteredByQuantity, keepSecond});
        when(client.getAccountHash()).thenReturn(123456789L);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Zezima");
        when(client.getAccountType()).thenReturn(AccountType.IRONMAN);

        BankCaptureService service = new BankCaptureService(client);
        BankSnapshot snapshot = service.captureFrom(container);

        Assert.assertNotNull(snapshot);
        Assert.assertEquals(123456789L, snapshot.getAccountHash());
        Assert.assertEquals("Zezima", snapshot.getDisplayName());
        Assert.assertEquals("IRONMAN", snapshot.getAccountType());
        Assert.assertEquals(BankCaptureService.PLUGIN_VERSION, snapshot.getPluginVersion());
        Assert.assertNotNull(UUID.fromString(snapshot.getSnapshotId()));
        Assert.assertEquals(0, Instant.parse(snapshot.getCapturedAt()).getNano());

        List<BankItem> items = snapshot.getItems();
        Assert.assertEquals(2, items.size());
        Assert.assertEquals(0, items.get(0).getSlot());
        Assert.assertEquals(995, items.get(0).getItemId());
        Assert.assertEquals(100, items.get(0).getQuantity());
        Assert.assertEquals(3, items.get(1).getSlot());
        Assert.assertEquals(11840, items.get(1).getItemId());
        Assert.assertEquals(7, items.get(1).getQuantity());
    }
}
