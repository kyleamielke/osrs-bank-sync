package io.kyil.osrsbanksync;

import java.util.List;

public final class BankSnapshot
{
    private final long accountHash;
    private final String displayName;
    private final String accountType;
    private final String capturedAt;
    private final String pluginVersion;
    private final String snapshotId;
    private final List<BankItem> items;

    public BankSnapshot(
        long accountHash,
        String displayName,
        String accountType,
        String capturedAt,
        String pluginVersion,
        String snapshotId,
        List<BankItem> items
    )
    {
        this.accountHash = accountHash;
        this.displayName = displayName;
        this.accountType = accountType;
        this.capturedAt = capturedAt;
        this.pluginVersion = pluginVersion;
        this.snapshotId = snapshotId;
        this.items = items;
    }

    public long getAccountHash()
    {
        return accountHash;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getAccountType()
    {
        return accountType;
    }

    public String getCapturedAt()
    {
        return capturedAt;
    }

    public String getPluginVersion()
    {
        return pluginVersion;
    }

    public String getSnapshotId()
    {
        return snapshotId;
    }

    public List<BankItem> getItems()
    {
        return items;
    }
}
