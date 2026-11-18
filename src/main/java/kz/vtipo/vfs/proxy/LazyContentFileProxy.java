package kz.vtipo.vfs.proxy;

import kz.vtipo.vfs.model.VfsFile;
import java.util.Objects;
import java.util.function.Supplier;

public class LazyContentFileProxy extends VfsFile {
    private Supplier<byte[]> loader;
    private final long declaredSize;
    private volatile boolean loaded;

    public LazyContentFileProxy(String name, int permissions, long declaredSize, Supplier<byte[]> loader) {
        super(name, permissions, new byte[0]);
        if (declaredSize < 0) throw new IllegalArgumentException("declaredSize must not be negative");
        this.declaredSize = declaredSize;
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        byte[] data = Objects.requireNonNull(loader.get(), "loader returned null");
        if (data.length != declaredSize) {
            throw new IllegalStateException("Loaded content size does not match declared size");
        }
        super.writeAt(0, data);
        loader = null;
        loaded = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public byte[] readAt(long position, int length) {
        ensureLoaded();
        return super.readAt(position, length);
    }

    @Override
    public int writeAt(long position, byte[] bytes) {
        ensureLoaded();
        return super.writeAt(position, bytes);
    }

    @Override
    public void truncate() {
        synchronized (this) {
            loader = null;
            loaded = true;
        }
        super.truncate();
    }

    @Override
    public long getSize() {
        return loaded ? super.getSize() : declaredSize;
    }
}
