package kz.vtipo.vfs.api;

public interface VirtualFileSystem {
    int open(String path, boolean create, boolean truncate);
    byte[] read(int fd, int length);
    int write(int fd, byte[] data);
    void seek(int fd, long position);
    void close(int fd);
    void mkdir(String path);
    void rename(String oldPath, String newPath);
    void link(String existingPath, String newPath);
    void symlink(String targetPath, String linkPath);
    FileStat stat(String path);
    void delete(String path);
}
