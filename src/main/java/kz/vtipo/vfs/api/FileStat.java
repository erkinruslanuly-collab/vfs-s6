package kz.vtipo.vfs.api;

public record FileStat(String name, NodeType type, int permissions, long size, int linkCount, long createdAt, long modifiedAt) {
    public enum NodeType { FILE, DIRECTORY, SYMLINK }
}
