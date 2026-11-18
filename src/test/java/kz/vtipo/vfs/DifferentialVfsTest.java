package kz.vtipo.vfs;

import kz.vtipo.vfs.api.FileStat;
import kz.vtipo.vfs.core.DefaultVirtualFileSystem;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DifferentialVfsTest {

    @ParameterizedTest
    @ValueSource(longs = {42L, 1337L, 2026L, 99999L, 777777L})
    void randomOperationsMatchNioBehavior(long seed, @TempDir Path tempDir) {
        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
        Random rnd = new Random(seed);

        for (int step = 0; step < 1000; step++) {
            int op = rnd.nextInt(6);
            String name = "/item_" + rnd.nextInt(12);
            Path realPath = tempDir.resolve(name.substring(1));

            switch (op) {
                case 0 -> {
                    boolean vfsOk = runVfs(() -> vfs.mkdir(name));
                    boolean nioOk = runNio(() -> Files.createDirectory(realPath));
                    assertEquals(nioOk, vfsOk, "Seed=" + seed + " step=" + step + " mkdir " + name);
                }
                case 1 -> {
                    byte[] data = ("payload-" + step).getBytes();
                    boolean vfsOk = runVfs(() -> {
                        int fd = vfs.open(name, true, true);
                        vfs.write(fd, data);
                        vfs.close(fd);
                    });
                    boolean nioOk = runNio(() -> {
                        if (Files.isDirectory(realPath)) throw new Exception("EISDIR");
                        Files.write(realPath, data);
                    });
                    assertEquals(nioOk, vfsOk, "Seed=" + seed + " step=" + step + " write " + name);
                }
                case 2 -> {
                    byte[] vfsBytes = readVfs(vfs, name);
                    byte[] nioBytes = readNio(realPath);
                    assertArrayEquals(nioBytes, vfsBytes, "Seed=" + seed + " step=" + step + " read " + name);
                }
                case 3 -> {
                    boolean vfsOk = runVfs(() -> vfs.delete(name));
                    boolean nioOk = runNio(() -> Files.delete(realPath));
                    assertEquals(nioOk, vfsOk, "Seed=" + seed + " step=" + step + " delete " + name);
                }
                case 4 -> {
                    boolean nioExists = Files.exists(realPath);
                    boolean vfsExists = runVfs(() -> {
                        FileStat stat = vfs.stat(name);
                        assertEquals(Files.isDirectory(realPath) ? FileStat.NodeType.DIRECTORY : FileStat.NodeType.FILE,
                            stat.type());
                    });
                    assertEquals(nioExists, vfsExists, "Seed=" + seed + " step=" + step + " stat " + name);
                }
                case 5 -> {
                    String target = "/item_" + rnd.nextInt(12);
                    Path realTarget = tempDir.resolve(target.substring(1));
                    if (!name.equals(target)) {
                        boolean sourceWasDirectory = Files.isDirectory(realPath);
                        boolean targetWasDirectory = Files.isDirectory(realTarget);
                        FileStat vfsSource = statOrNull(vfs, name);
                        FileStat vfsTarget = statOrNull(vfs, target);
                        String vfsFailure = "";
                        boolean vfsOk;
                        try {
                            vfs.rename(name, target);
                            vfsOk = true;
                        } catch (RuntimeException exception) {
                            vfsOk = false;
                            vfsFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                        }
                        boolean nioOk = runNio(() -> {
                            if (Files.isDirectory(realTarget) && isDirectoryNotEmpty(realTarget)) {
                                throw new Exception("ENOTEMPTY");
                            }
                            Files.move(realPath, realTarget, StandardCopyOption.REPLACE_EXISTING);
                        });
                        assertEquals(nioOk, vfsOk,
                            "Seed=" + seed + " step=" + step + " rename " + name + "->" + target
                                + " (sourceDirectory=" + sourceWasDirectory
                                + ", targetDirectory=" + targetWasDirectory
                                + ", vfsSource=" + vfsSource + ", vfsTarget=" + vfsTarget
                                + ", vfsFailure=" + vfsFailure + ")");
                    }
                }
                default -> throw new AssertionError("Unexpected operation");
            }
            assertStateMatches(vfs, tempDir, seed, step);
        }
    }

    private byte[] readVfs(DefaultVirtualFileSystem vfs, String path) {
        try {
            int fd = vfs.open(path, false, false);
            byte[] data = vfs.read(fd, 4096);
            vfs.close(fd);
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readNio(Path path) {
        try {
            if (Files.isDirectory(path)) return null;
            return Files.readAllBytes(path);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDirectoryNotEmpty(Path path) throws Exception {
        try (var entries = Files.list(path)) {
            return entries.findAny().isPresent();
        }
    }

    private FileStat statOrNull(DefaultVirtualFileSystem vfs, String path) {
        try {
            return vfs.stat(path);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void assertStateMatches(DefaultVirtualFileSystem vfs, Path tempDir, long seed, int step) {
        for (int index = 0; index < 12; index++) {
            String name = "/item_" + index;
            Path realPath = tempDir.resolve(name.substring(1));
            FileStat vfsStat = statOrNull(vfs, name);
            boolean nioExists = Files.exists(realPath);
            assertEquals(nioExists, vfsStat != null, "Seed=" + seed + " step=" + step + " existence " + name);
            if (!nioExists) continue;

            FileStat.NodeType nioType = Files.isDirectory(realPath)
                ? FileStat.NodeType.DIRECTORY
                : FileStat.NodeType.FILE;
            assertEquals(nioType, vfsStat.type(), "Seed=" + seed + " step=" + step + " type " + name);
            if (nioType == FileStat.NodeType.FILE) {
                assertArrayEquals(readNio(realPath), readVfs(vfs, name),
                    "Seed=" + seed + " step=" + step + " content " + name);
            }
        }
    }

    private boolean runVfs(Runnable action) {
        try {
            action.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean runNio(ThrowingRunnable action) {
        try {
            action.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
