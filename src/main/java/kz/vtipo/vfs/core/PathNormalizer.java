package kz.vtipo.vfs.core;

import kz.vtipo.vfs.api.VfsException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class PathNormalizer {
    private PathNormalizer() {}

    public static List<String> normalizeToSegments(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || !rawPath.startsWith("/")) {
            throw new VfsException("EINVAL", "Жол '/' таңбасынан басталуы тиіс: " + rawPath);
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String part : rawPath.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!segments.isEmpty()) segments.removeLast();
            } else {
                segments.addLast(part);
            }
        }
        return List.copyOf(segments);
    }

    public static String normalize(String rawPath) {
        List<String> segments = normalizeToSegments(rawPath);
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }
}
