package kz.vtipo.vfs.iterator;

import kz.vtipo.vfs.model.VfsDirectory;
import kz.vtipo.vfs.model.VfsNode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LazyDirectoryIterator implements Iterator<VfsNode> {
    private final Deque<Iterator<VfsNode>> stack = new ArrayDeque<>();

    public LazyDirectoryIterator(VfsDirectory root) {
        stack.push(root.getChildrenSnapshot().iterator());
    }

    @Override
    public boolean hasNext() {
        while (!stack.isEmpty()) {
            if (stack.peek().hasNext()) return true;
            stack.pop();
        }
        return false;
    }

    @Override
    public VfsNode next() {
        if (!hasNext()) throw new NoSuchElementException();
        VfsNode current = stack.peek().next();
        if (current instanceof VfsDirectory directory) {
            stack.push(directory.getChildrenSnapshot().iterator());
        }
        return current;
    }
}
