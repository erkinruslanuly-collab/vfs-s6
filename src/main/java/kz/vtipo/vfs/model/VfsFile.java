package kz.vtipo.vfs.model;

import kz.vtipo.vfs.api.FileStat.NodeType;
import kz.vtipo.vfs.api.VfsException;
import kz.vtipo.vfs.visitor.VfsVisitor;
import java.util.Arrays;

public class VfsFile implements VfsNode {
    private String name;
    private final Inode inode;

    public VfsFile(String name, int permissions, byte[] data) {
        this.name = name;
        this.inode = new Inode(permissions, data);
    }
    public VfsFile(String name, Inode sharedInode) {
        this.name = name;
        this.inode = sharedInode;
        this.inode.incrementLinks();
    }
    public Inode getInode() { return inode; }
    public byte[] readAt(long position, int length) {
        if (position < 0) throw new VfsException("EINVAL", "Теріс позиция");
        byte[] content = inode.getData();
        if (position >= content.length || length <= 0) return new byte[0];
        int start = (int) position;
        int end = Math.min(content.length, start + length);
        return Arrays.copyOfRange(content, start, end);
    }
    public int writeAt(long position, byte[] bytes) {
        if (position < 0) throw new VfsException("EINVAL", "Теріс позиция");
        if (bytes == null || bytes.length == 0) return 0;
        if (position > Integer.MAX_VALUE || bytes.length > Integer.MAX_VALUE - position) {
            throw new VfsException("EFBIG", "Файл өлшемі рұқсат етілген шектен асты");
        }
        byte[] current = inode.getData();
        int start = (int) position;
        int requiredSize = Math.max(current.length, start + bytes.length);
        byte[] updated = Arrays.copyOf(current, requiredSize);
        System.arraycopy(bytes, 0, updated, start, bytes.length);
        inode.setData(updated);
        return bytes.length;
    }
    public void truncate() { inode.setData(new byte[0]); }
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public NodeType getType() { return NodeType.FILE; }
    @Override public int getPermissions() { return inode.getPermissions(); }
    @Override public void setPermissions(int permissions) { inode.setPermissions(permissions); }
    @Override public long getSize() { return inode.getSize(); }
    @Override public int getLinkCount() { return inode.getLinkCount(); }
    @Override public long getCreatedAt() { return inode.getCreatedAt(); }
    @Override public long getModifiedAt() { return inode.getModifiedAt(); }
    @Override public <T> T accept(VfsVisitor<T> visitor) { return visitor.visitFile(this); }
}
