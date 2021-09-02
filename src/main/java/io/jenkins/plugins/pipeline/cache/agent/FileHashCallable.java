package io.jenkins.plugins.pipeline.cache.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.IteratorUtils;

import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

/**
 * Apply a filter a given folder and then hash the content.
 */
public class FileHashCallable extends MasterToSlaveFileCallable<String> {

    private final String files;

    public FileHashCallable(String files) {
        this.files = files;
    }

    @Override
    public String invoke(File f, VirtualChannel channel) throws IOException {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + files);
        Stream<FileInputStream> streams = Files.walk(f.toPath())
                .filter(pathMatcher::matches)
                .sorted()
                .map(path -> {
                    try {
                        return new FileInputStream(path.toFile());
                    } catch (FileNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                });

        try (SequenceInputStream sequenceInputStream = new SequenceInputStream(IteratorUtils.asEnumeration(streams.iterator()))) {
            return DigestUtils.md5Hex(sequenceInputStream);
        }
    }
}
