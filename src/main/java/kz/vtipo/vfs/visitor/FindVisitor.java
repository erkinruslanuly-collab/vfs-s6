package kz.vtipo.vfs.visitor;

import kz.vtipo.vfs.model.VfsDirectory;
import kz.vtipo.vfs.model.VfsFile;
import kz.vtipo.vfs.model.VfsNode;
import kz.vtipo.vfs.model.VfsSymLink;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class FindVisitor implements VfsVisitor<List<String>> {
    private final Predicate<VfsNode> predicate;
    private final String currentPrefix;

    public FindVisitor(Predicate<VfsNode> predicate) {
        this(predicate, "");
    }

    private FindVisitor(Predicate<VfsNode> predicate, String prefix) {
        this.predicate = predicate;
        this.currentPrefix = prefix;
    }

    @Override
    public List<String> visitFile(VfsFile file) {
        String path = currentPrefix + "/" + file.getName();
        return predicate.test(file) ? List.of(path) : List.of();
    }

    @Override
    public List<String> visitSymLink(VfsSymLink symLink) {
        String path = currentPrefix + "/" + symLink.getName();
        return predicate.test(symLink) ? List.of(path) : List.of();
    }

    @Override
    public List<String> visitDirectory(VfsDirectory directory) {
        List<String> results = new ArrayList<>();
        String dirPath = directory.getName().equals("/")
            ? ""
            : currentPrefix + "/" + directory.getName();
        if (!directory.getName().equals("/") && predicate.test(directory)) {
            results.add(dirPath);
        }
        for (VfsNode child : directory.getChildrenSnapshot()) {
            results.addAll(child.accept(new FindVisitor(predicate, dirPath)));
        }
        return results;
    }
}
