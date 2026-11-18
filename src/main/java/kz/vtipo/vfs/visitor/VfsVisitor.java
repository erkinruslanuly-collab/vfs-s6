package kz.vtipo.vfs.visitor;

import kz.vtipo.vfs.model.VfsDirectory;
import kz.vtipo.vfs.model.VfsFile;
import kz.vtipo.vfs.model.VfsSymLink;

public interface VfsVisitor<T> {
    T visitFile(VfsFile file);
    T visitDirectory(VfsDirectory directory);
    T visitSymLink(VfsSymLink symLink);
}
