package io.kyil.osrsbanksync;

public final class BankItem
{
    private final int slot;
    private final int itemId;
    private final int quantity;

    public BankItem(int slot, int itemId, int quantity)
    {
        this.slot = slot;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public int getSlot()
    {
        return slot;
    }

    public int getItemId()
    {
        return itemId;
    }

    public int getQuantity()
    {
        return quantity;
    }
}
