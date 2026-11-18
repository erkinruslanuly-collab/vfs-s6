package kz.vtipo.vfs.visitor;

import kz.vtipo.vfs.model.VfsDirectory;
import kz.vtipo.vfs.model.VfsFile;
import kz.vtipo.vfs.model.VfsNode;
import kz.vtipo.vfs.model.VfsSymLink;

public class DuVisitor implements VfsVisitor<Long> {
    @Override
    public Long visitFile(VfsFile file) {
        return file.getSize();
    }

    @Override
    public Long visitSymLink(VfsSymLink symLink) {
        return symLink.getSize();
    }

    @Override
    public Long visitDirectory(VfsDirectory directory) {
        long total = 0;
        for (VfsNode child : directory.getChildrenSnapshot()) {
            total += child.accept(this);
        }
        return total;
    }
}
