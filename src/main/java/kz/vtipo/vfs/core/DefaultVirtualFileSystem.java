package kz.vtipo.vfs.core;

import kz.vtipo.vfs.api.FileStat;
import kz.vtipo.vfs.api.VfsException;
import kz.vtipo.vfs.api.VirtualFileSystem;
import kz.vtipo.vfs.iterator.LazyDirectoryIterator;
import kz.vtipo.vfs.model.VfsDirectory;
import kz.vtipo.vfs.model.VfsFile;
import kz.vtipo.vfs.model.VfsNode;
import kz.vtipo.vfs.model.VfsSymLink;
import kz.vtipo.vfs.proxy.SecurityProxyNode;
import kz.vtipo.vfs.visitor.ChmodVisitor;
import kz.vtipo.vfs.visitor.DuVisitor;
import kz.vtipo.vfs.visitor.FindVisitor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

public class DefaultVirtualFileSystem implements VirtualFileSystem {
    private static final int MAX_SYMLINK_DEPTH = 40;
    private final VfsDirectory root = new VfsDirectory("/", 0755);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicInteger nextDescriptor = new AtomicInteger(3);
    private final Map<Integer, FileDescriptor> openFiles = new ConcurrentHashMap<>();

    private static final class FileDescriptor {
        private final VfsFile file;
        private long position;

        private FileDescriptor(VfsFile file) {
            this.file = file;
        }
    }

    @Override
    public int open(String path, boolean create, boolean truncate) {
        lock.writeLock().lock();
        try {
            List<String> segments = PathNormalizer.normalizeToSegments(path);
            if (segments.isEmpty()) throw new VfsException("EISDIR", "Каталогты файл ретінде ашу мүмкін емес");
            VfsDirectory parent = resolveParentDirectory(segments);
            String name = segments.get(segments.size() - 1);
            Optional<VfsNode> existing = parent.getChild(name);
            VfsNode target;
            if (existing.isPresent()) {
                target = resolveIfSymLink(existing.get(), 0);
            } else {
                if (!create) throw new VfsException("ENOENT", "Файл табылмады: " + path);
                checkWritePermission(parent);
                VfsFile file = new VfsFile(name, 0644, new byte[0]);
                parent.putChild(name, file);
                target = file;
            }
            if (target instanceof VfsDirectory) throw new VfsException("EISDIR", "Бұл каталог: " + path);
            if (!(target instanceof VfsFile file)) throw new VfsException("EINVAL", "Файл түрі жарамсыз");
            if (truncate) {
                checkWritePermission(file);
                file.truncate();
            }
            int descriptor = nextDescriptor.getAndIncrement();
            openFiles.put(descriptor, new FileDescriptor(file));
            return descriptor;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public byte[] read(int fd, int length) {
        if (length < 0) throw new VfsException("EINVAL", "Оқу ұзындығы теріс болмауы тиіс");
        lock.readLock().lock();
        try {
            FileDescriptor descriptor = getDescriptor(fd);
            checkReadPermission(descriptor.file);
            synchronized (descriptor) {
                byte[] result = descriptor.file.readAt(descriptor.position, length);
                descriptor.position += result.length;
                return result;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int write(int fd, byte[] data) {
        lock.writeLock().lock();
        try {
            FileDescriptor descriptor = getDescriptor(fd);
            checkWritePermission(descriptor.file);
            synchronized (descriptor) {
                int written = descriptor.file.writeAt(descriptor.position, data);
                descriptor.position += written;
                return written;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void seek(int fd, long position) {
        if (position < 0) throw new VfsException("EINVAL", "Теріс позиция");
        lock.readLock().lock();
        try {
            FileDescriptor descriptor = getDescriptor(fd);
            synchronized (descriptor) {
                descriptor.position = position;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close(int fd) {
        if (openFiles.remove(fd) == null) throw new VfsException("EBADF", "Жарамсыз дескриптор: " + fd);
    }

    @Override
    public void mkdir(String path) {
        lock.writeLock().lock();
        try {
            List<String> segments = PathNormalizer.normalizeToSegments(path);
            if (segments.isEmpty()) throw new VfsException("EEXIST", "Түбірлік каталог бұрыннан бар");
            VfsDirectory parent = resolveParentDirectory(segments);
            checkWritePermission(parent);
            String name = segments.get(segments.size() - 1);
            if (parent.getChild(name).isPresent()) throw new VfsException("EEXIST", "Каталог бар: " + path);
            parent.putChild(name, new VfsDirectory(name, 0755));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void rename(String oldPath, String newPath) {
        lock.writeLock().lock();
        try {
            List<String> sourceSegments = PathNormalizer.normalizeToSegments(oldPath);
            List<String> targetSegments = PathNormalizer.normalizeToSegments(newPath);
            if (sourceSegments.isEmpty() || targetSegments.isEmpty()) {
                throw new VfsException("EBUSY", "Түбірлік каталог өзгермейді");
            }
            if (sourceSegments.equals(targetSegments)) return;
            VfsDirectory sourceParent = resolveParentDirectory(sourceSegments);
            VfsDirectory targetParent = resolveParentDirectory(targetSegments);
            checkWritePermission(sourceParent);
            checkWritePermission(targetParent);
            String sourceName = sourceSegments.get(sourceSegments.size() - 1);
            String targetName = targetSegments.get(targetSegments.size() - 1);
            VfsNode source = sourceParent.getChild(sourceName)
                .orElseThrow(() -> new VfsException("ENOENT", "Бастапқы файл табылмады: " + oldPath));
            if (source instanceof VfsDirectory && targetSegments.size() > sourceSegments.size()
                && targetSegments.subList(0, sourceSegments.size()).equals(sourceSegments)) {
                throw new VfsException("EINVAL", "Каталогты өз ішіне жылжытуға болмайды");
            }
            Optional<VfsNode> existingTarget = targetParent.getChild(targetName);
            if (existingTarget.isPresent()) {
                VfsNode target = existingTarget.get();
                if (target instanceof VfsDirectory targetDirectory) {
                    if (!targetDirectory.isEmpty()) throw new VfsException("ENOTEMPTY", "Нысана каталог бос емес");
                }
                removeNode(targetParent, targetName, target);
            }
            sourceParent.removeChild(sourceName);
            source.setName(targetName);
            targetParent.putChild(targetName, source);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void link(String existingPath, String newPath) {
        lock.writeLock().lock();
        try {
            VfsNode target = resolveNode(PathNormalizer.normalizeToSegments(existingPath), 0, true);
            if (!(target instanceof VfsFile file)) throw new VfsException("EPERM", "Тек файлға hard link жасалады");
            List<String> targetSegments = PathNormalizer.normalizeToSegments(newPath);
            if (targetSegments.isEmpty()) throw new VfsException("EEXIST", "Түбірге сілтеме жасалмайды");
            VfsDirectory parent = resolveParentDirectory(targetSegments);
            checkWritePermission(parent);
            String name = targetSegments.get(targetSegments.size() - 1);
            if (parent.getChild(name).isPresent()) throw new VfsException("EEXIST", "Файл бар: " + newPath);
            parent.putChild(name, new VfsFile(name, file.getInode()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void symlink(String targetPath, String linkPath) {
        if (targetPath == null || targetPath.isEmpty()) throw new VfsException("EINVAL", "Сілтеме нысанасы бос");
        lock.writeLock().lock();
        try {
            List<String> segments = PathNormalizer.normalizeToSegments(linkPath);
            if (segments.isEmpty()) throw new VfsException("EEXIST", "Түбірлік каталогқа сілтеме жасалмайды");
            VfsDirectory parent = resolveParentDirectory(segments);
            checkWritePermission(parent);
            String name = segments.get(segments.size() - 1);
            if (parent.getChild(name).isPresent()) throw new VfsException("EEXIST", "Нысан бар: " + linkPath);
            parent.putChild(name, new VfsSymLink(name, targetPath));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public FileStat stat(String path) {
        lock.readLock().lock();
        try {
            return toStat(resolveNode(PathNormalizer.normalizeToSegments(path), 0, true));
        } finally {
            lock.readLock().unlock();
        }
    }

    public FileStat lstat(String path) {
        lock.readLock().lock();
        try {
            return toStat(resolveNode(PathNormalizer.normalizeToSegments(path), 0, false));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String path) {
        lock.writeLock().lock();
        try {
            List<String> segments = PathNormalizer.normalizeToSegments(path);
            if (segments.isEmpty()) throw new VfsException("EBUSY", "Түбірлік каталог жойылмайды");
            VfsDirectory parent = resolveParentDirectory(segments);
            checkWritePermission(parent);
            String name = segments.get(segments.size() - 1);
            VfsNode node = parent.getChild(name)
                .orElseThrow(() -> new VfsException("ENOENT", "Табылмады: " + path));
            if (node instanceof VfsDirectory directory && !directory.isEmpty()) {
                throw new VfsException("ENOTEMPTY", "Каталог бос емес: " + path);
            }
            removeNode(parent, name, node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void mountNode(String parentPath, VfsNode node) {
        lock.writeLock().lock();
        try {
            VfsNode parent = resolveNode(PathNormalizer.normalizeToSegments(parentPath), 0, true);
            if (!(parent instanceof VfsDirectory directory)) throw new VfsException("ENOTDIR", "Каталог емес");
            checkWritePermission(directory);
            if (directory.getChild(node.getName()).isPresent()) {
                throw new VfsException("EEXIST", "Нысан бар: " + node.getName());
            }
            directory.putChild(node.getName(), node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long du(String path) {
        lock.readLock().lock();
        try {
            VfsNode node = resolveNode(PathNormalizer.normalizeToSegments(path), 0, true);
            return new SecurityProxyNode(node, 4).accept(new DuVisitor());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> find(String path, Predicate<VfsNode> predicate) {
        lock.readLock().lock();
        try {
            VfsNode node = resolveNode(PathNormalizer.normalizeToSegments(path), 0, true);
            return new SecurityProxyNode(node, 4).accept(new FindVisitor(predicate));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void chmodRecursive(String path, int permissions) {
        lock.writeLock().lock();
        try {
            VfsNode node = resolveNode(PathNormalizer.normalizeToSegments(path), 0, true);
            node.accept(new ChmodVisitor(permissions));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Iterator<VfsNode> lazyWalk(String path) {
        lock.readLock().lock();
        try {
            VfsNode node = resolveNode(PathNormalizer.normalizeToSegments(path), 0, true);
            if (!(node instanceof VfsDirectory directory)) throw new VfsException("ENOTDIR", "Каталог емес");
            return new LazyDirectoryIterator(directory);
        } finally {
            lock.readLock().unlock();
        }
    }

    private FileStat toStat(VfsNode node) {
        return new FileStat(node.getName(), node.getType(), node.getPermissions(), node.getSize(),
            node.getLinkCount(), node.getCreatedAt(), node.getModifiedAt());
    }

    private FileDescriptor getDescriptor(int fd) {
        FileDescriptor descriptor = openFiles.get(fd);
        if (descriptor == null) throw new VfsException("EBADF", "Файл ашылмаған: " + fd);
        return descriptor;
    }

    private void checkReadPermission(VfsNode node) {
        checkPermission(node, 4);
    }

    private void checkWritePermission(VfsNode node) {
        checkPermission(node, 2);
    }

    private void checkPermission(VfsNode node, int mask) {
        int ownerPermissions = (node.getPermissions() >> 6) & 7;
        if ((ownerPermissions & mask) != mask) {
            throw new VfsException("EACCES", "Рұқсат жоқ: " + node.getName());
        }
    }

    private VfsDirectory resolveParentDirectory(List<String> segments) {
        if (segments.size() <= 1) return root;
        VfsNode parent = resolveNode(segments.subList(0, segments.size() - 1), 0, true);
        if (!(parent instanceof VfsDirectory directory)) throw new VfsException("ENOTDIR", "Ата-аналық жол каталог емес");
        return directory;
    }

    private void removeNode(VfsDirectory parent, String name, VfsNode node) {
        parent.removeChild(name);
        if (node instanceof VfsFile file) file.getInode().decrementLinks();
    }

    private VfsNode resolveIfSymLink(VfsNode node, int depth) {
        if (!(node instanceof VfsSymLink link)) return node;
        if (depth >= MAX_SYMLINK_DEPTH) throw new VfsException("ELOOP", "Сілтеме циклі анықталды");
        return resolveNode(PathNormalizer.normalizeToSegments(link.getTargetPath()), depth + 1, true);
    }

    private VfsNode resolveNode(List<String> segments, int depth, boolean followLast) {
        if (depth > MAX_SYMLINK_DEPTH) throw new VfsException("ELOOP", "Сілтеме циклі анықталды");
        VfsNode current = root;
        for (int index = 0; index < segments.size(); index++) {
            if (!(current instanceof VfsDirectory directory)) throw new VfsException("ENOTDIR", "Каталог емес");
            String name = segments.get(index);
            current = directory.getChild(name)
                .orElseThrow(() -> new VfsException("ENOENT", "Табылмады: " + name));
            boolean isLast = index == segments.size() - 1;
            if (current instanceof VfsSymLink link && (!isLast || followLast)) {
                if (depth >= MAX_SYMLINK_DEPTH) throw new VfsException("ELOOP", "Сілтеме циклі анықталды");
                List<String> resolved = new ArrayList<>(PathNormalizer.normalizeToSegments(link.getTargetPath()));
                resolved.addAll(segments.subList(index + 1, segments.size()));
                return resolveNode(resolved, depth + 1, followLast);
            }
        }
        return current;
    }
}
