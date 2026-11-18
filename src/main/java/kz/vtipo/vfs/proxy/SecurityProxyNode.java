package kz.vtipo.vfs.proxy;

import kz.vtipo.vfs.api.FileStat.NodeType;
import kz.vtipo.vfs.api.VfsException;
import kz.vtipo.vfs.model.VfsNode;
import kz.vtipo.vfs.visitor.VfsVisitor;
import java.util.Objects;

public class SecurityProxyNode implements VfsNode {
    private final VfsNode target;
    private final int requiredMask;

    public SecurityProxyNode(VfsNode target, int requiredMask) {
        this.target = Objects.requireNonNull(target, "target");
        this.requiredMask = requiredMask;
    }

    private void checkAccess() {
        int ownerPermissions = (target.getPermissions() >> 6) & 7;
        if ((ownerPermissions & requiredMask) != requiredMask) {
            throw new VfsException("EACCES", "Құқық жеткіліксіз: " + target.getName());
        }
    }

    @Override
    public <T> T accept(VfsVisitor<T> visitor) {
        checkAccess();
        return target.accept(visitor);
    }

    @Override public String getName() { return target.getName(); }
    @Override public void setName(String name) { checkAccess(); target.setName(name); }
    @Override public NodeType getType() { return target.getType(); }
    @Override public int getPermissions() { return target.getPermissions(); }
    @Override public void setPermissions(int permissions) { checkAccess(); target.setPermissions(permissions); }
    @Override public long getSize() { checkAccess(); return target.getSize(); }
    @Override public int getLinkCount() { return target.getLinkCount(); }
    @Override public long getCreatedAt() { return target.getCreatedAt(); }
    @Override public long getModifiedAt() { return target.getModifiedAt(); }
}
