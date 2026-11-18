package kz.vtipo.vfs.model;

import kz.vtipo.vfs.api.FileStat.NodeType;
import kz.vtipo.vfs.visitor.VfsVisitor;

public class VfsSymLink implements VfsNode {
    private String name;
    private final String targetPath;
    private int permissions = 0777;
    private final long createdAt = System.currentTimeMillis();

    public VfsSymLink(String name, String targetPath) {
        this.name = name;
        this.targetPath = targetPath;
    }
    public String getTargetPath() { return targetPath; }
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public NodeType getType() { return NodeType.SYMLINK; }
    @Override public int getPermissions() { return permissions; }
    @Override public void setPermissions(int permissions) { this.permissions = permissions; }
    @Override public long getSize() { return targetPath.length(); }
    @Override public int getLinkCount() { return 1; }
    @Override public long getCreatedAt() { return createdAt; }
    @Override public long getModifiedAt() { return createdAt; }
    @Override public <T> T accept(VfsVisitor<T> visitor) { return visitor.visitSymLink(this); }
}
