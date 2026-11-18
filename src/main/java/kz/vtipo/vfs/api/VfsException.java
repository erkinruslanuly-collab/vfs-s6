package kz.vtipo.vfs.api;

public class VfsException extends RuntimeException {
    private final String posixCode;
    public VfsException(String posixCode, String message) {
        super("[" + posixCode + "] " + message);
        this.posixCode = posixCode;
    }
    public String getPosixCode() { return posixCode; }
}
