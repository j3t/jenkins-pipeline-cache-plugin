package io.jenkins.plugins.pipeline.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.IteratorUtils;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;

public class CacheStepExecution extends GeneralNonBlockingStepExecution {

    private static final long serialVersionUID = 1L;

    private final transient Cache cache;
    private final CacheStep step;

    public CacheStepExecution(StepContext context, CacheStep step) throws IOException, InterruptedException {
        this(context, step, new Cache(Configuration.get(), context.get(TaskListener.class).getLogger()));
    }

    public CacheStepExecution(StepContext context, CacheStep step, Cache cache) {
        super(context);
        this.step = step;
        this.cache = cache;
    }

    /**
     * Collects relevant workspace files (always in the same order, hopefully) and then the content gets hashed and the value will be
     * returned. The hash should be consistent as long as the relevant files are the same.
     */
    String createFileHash() throws IOException, InterruptedException {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + step.getHashFiles());
        Path workspace = Paths.get(getContext().get(EnvVars.class).get("WORKSPACE"));
        Stream<FileInputStream> streams = Files.walk(workspace)
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

    private String[] createRestoreKeys() throws IOException, InterruptedException {
        String type = getContext().get(EnvVars.class).expand(step.getType());
        String hash = createFileHash();

        return new String[]{type + "-" + hash, type};
    }

    @Override
    public boolean start() throws Exception {
        FilePath folder = new FilePath(new File(step.getFolder()));
        String[] restoreKeys = createRestoreKeys();

        // restore folder
        cache.restore(folder, restoreKeys);

        // execute step and backup folder after completion
        getContext().newBodyInvoker().withCallback(new BodyExecutionCallback.TailCall() {
            @Override
            protected void finished(StepContext context) throws Exception {
                cache.backup(folder, restoreKeys[0]);
            }
        }).start();

        return false;
    }

}
