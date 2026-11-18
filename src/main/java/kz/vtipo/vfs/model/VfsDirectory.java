package kz.vtipo.vfs.model;

import kz.vtipo.vfs.api.FileStat.NodeType;
import kz.vtipo.vfs.visitor.VfsVisitor;
import java.util.*;

public class VfsDirectory implements VfsNode {
    private String name;
    private int permissions;
    private final Map<String, VfsNode> children = new LinkedHashMap<>();
    private final long createdAt = System.currentTimeMillis();
    private long modifiedAt = createdAt;

    public VfsDirectory(String name, int permissions) {
        this.name = name;
        this.permissions = permissions;
    }
    public void putChild(String childName, VfsNode node) { children.put(childName, node); modifiedAt = System.currentTimeMillis(); }
    public Optional<VfsNode> getChild(String childName) { return Optional.ofNullable(children.get(childName)); }
    public VfsNode removeChild(String childName) { modifiedAt = System.currentTimeMillis(); return children.remove(childName); }
    public boolean isEmpty() { return children.isEmpty(); }
    public Collection<VfsNode> getChildrenSnapshot() { return List.copyOf(children.values()); }
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public NodeType getType() { return NodeType.DIRECTORY; }
    @Override public int getPermissions() { return permissions; }
    @Override public void setPermissions(int permissions) { this.permissions = permissions; }
    @Override public long getSize() { return children.size(); }
    @Override public int getLinkCount() { return 2; }
    @Override public long getCreatedAt() { return createdAt; }
    @Override public long getModifiedAt() { return modifiedAt; }
    @Override public <T> T accept(VfsVisitor<T> visitor) { return visitor.visitDirectory(this); }
}
