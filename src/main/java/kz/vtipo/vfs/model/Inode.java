package kz.vtipo.vfs.model;

import java.util.Arrays;

public class Inode {
    private byte[] data;
    private int permissions;
    private int linkCount;
    private final long createdAt;
    private long modifiedAt;

    public Inode(int permissions, byte[] initialData) {
        this.permissions = permissions;
        this.data = initialData != null ? Arrays.copyOf(initialData, initialData.length) : new byte[0];
        this.linkCount = 1;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.modifiedAt = now;
    }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; this.modifiedAt = System.currentTimeMillis(); }
    public int getPermissions() { return permissions; }
    public void setPermissions(int permissions) { this.permissions = permissions; }
    public int getLinkCount() { return linkCount; }
    public void incrementLinks() { this.linkCount++; }
    public void decrementLinks() { if (this.linkCount > 0) this.linkCount--; }
    public long getSize() { return data.length; }
    public long getCreatedAt() { return createdAt; }
    public long getModifiedAt() { return modifiedAt; }
}
