package kz.vtipo.vfs.visitor;

import kz.vtipo.vfs.model.VfsDirectory;
import kz.vtipo.vfs.model.VfsFile;
import kz.vtipo.vfs.model.VfsNode;
import kz.vtipo.vfs.model.VfsSymLink;

public class ChmodVisitor implements VfsVisitor<Void> {
    private final int newPermissions;

    public ChmodVisitor(int newPermissions) {
        this.newPermissions = newPermissions;
    }

    @Override
    public Void visitFile(VfsFile file) {
        file.setPermissions(newPermissions);
        return null;
    }

    @Override
    public Void visitSymLink(VfsSymLink symLink) {
        symLink.setPermissions(newPermissions);
        return null;
    }

    @Override
    public Void visitDirectory(VfsDirectory directory) {
        directory.setPermissions(newPermissions);
        for (VfsNode child : directory.getChildrenSnapshot()) {
            child.accept(this);
        }
        return null;
    }
}
