package kz.vtipo.vfs.model;

import kz.vtipo.vfs.api.FileStat.NodeType;
import kz.vtipo.vfs.visitor.VfsVisitor;

public interface VfsNode {
    String getName();
    void setName(String name);
    NodeType getType();
    int getPermissions();
    void setPermissions(int permissions);
    long getSize();
    int getLinkCount();
    long getCreatedAt();
    long getModifiedAt();
    <T> T accept(VfsVisitor<T> visitor);
}
