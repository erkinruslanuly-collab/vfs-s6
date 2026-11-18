package kz.vtipo.vfs;

import kz.vtipo.vfs.api.*;
import kz.vtipo.vfs.core.*;
import kz.vtipo.vfs.model.*;
import kz.vtipo.vfs.proxy.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class VfsComprehensiveTest {

    @Test
    void testPathNormalizationAndSymlinkLoop() {
        assertEquals("/a/c", PathNormalizer.normalize("/a//b/./../c/"));
        assertEquals("/", PathNormalizer.normalize("/../../.."));
        assertThrows(VfsException.class, () -> PathNormalizer.normalize("relative/path"));

        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.symlink("/loopB", "/loopA");
        vfs.symlink("/loopA", "/loopB");

        VfsException exception = assertThrows(VfsException.class, () -> vfs.stat("/loopA"));
        assertEquals("ELOOP", exception.getPosixCode());
        assertEquals(FileStat.NodeType.SYMLINK, vfs.lstat("/loopA").type());
    }

    @Test
    void testHardLinksAndFileOperations() {
        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.mkdir("/docs");
        int fd = vfs.open("/docs/note.txt", true, false);
        vfs.write(fd, "Salem VFS!".getBytes(StandardCharsets.UTF_8));
        vfs.seek(fd, 6);
        assertEquals("VFS!", new String(vfs.read(fd, 10), StandardCharsets.UTF_8));
        vfs.close(fd);

        vfs.link("/docs/note.txt", "/docs/hardlink.txt");
        assertEquals(2, vfs.stat("/docs/note.txt").linkCount());

        vfs.delete("/docs/note.txt");
        assertEquals(1, vfs.stat("/docs/hardlink.txt").linkCount());
    }

    @Test
    void testVisitorsLazyIteratorAndProxies() {
        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        vfs.mkdir("/projects");
        AtomicBoolean loaded = new AtomicBoolean(false);
        LazyContentFileProxy lazyFile = new LazyContentFileProxy("big.bin", 0644, 1024, () -> {
            loaded.set(true);
            return new byte[1024];
        });
        vfs.mountNode("/projects", lazyFile);

        assertFalse(loaded.get());
        assertEquals(1024, vfs.du("/projects"));
        assertFalse(loaded.get());

        int fd = vfs.open("/projects/big.bin", false, false);
        assertEquals(1024, vfs.read(fd, 2048).length);
        assertTrue(loaded.get());
        vfs.close(fd);

        assertEquals(List.of("/projects/big.bin"),
            vfs.find("/projects", node -> node.getName().endsWith(".bin")));

        List<String> walked = new ArrayList<>();
        Iterator<VfsNode> iterator = vfs.lazyWalk("/");
        while (iterator.hasNext()) walked.add(iterator.next().getName());
        assertTrue(walked.contains("big.bin"));

        vfs.chmodRecursive("/projects", 0000);
        VfsException securityException = assertThrows(VfsException.class, () -> vfs.du("/projects"));
        assertEquals("EACCES", securityException.getPosixCode());
    }

    @Test
    void testConcurrentReadWriteAndAtomicRename() throws Exception {
        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        int initialFd = vfs.open("/shared.txt", true, false);
        vfs.write(initialFd, "initial-data".getBytes(StandardCharsets.UTF_8));
        vfs.close(initialFd);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                final int index = i;
                tasks.add(() -> {
                    if (index % 2 == 0) {
                        vfs.stat("/shared.txt");
                    } else {
                        String temporaryPath = "/tmp_" + index;
                        int fd = vfs.open(temporaryPath, true, true);
                        vfs.write(fd, ("v" + index).getBytes(StandardCharsets.UTF_8));
                        vfs.close(fd);
                        vfs.rename(temporaryPath, "/shared.txt");
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks)) future.get(5, TimeUnit.SECONDS);
            assertTrue(vfs.stat("/shared.txt").size() > 0);
        } finally {
            pool.shutdownNow();
        }
    }
}
