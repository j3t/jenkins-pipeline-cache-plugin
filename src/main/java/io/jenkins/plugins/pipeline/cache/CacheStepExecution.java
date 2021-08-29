package io.jenkins.plugins.pipeline.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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

    private String createKeySuffix() throws IOException, InterruptedException {
        if (step.getHashFiles() == null) {
            return null;
        }

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + step.getHashFiles());

        Stream<FileInputStream> streams = Files.walk(Paths.get(getContext().get(EnvVars.class).get("WORKSPACE")))
                .filter(path -> pathMatcher.matches(path))
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

    String[] createRestoreKeys() throws IOException, InterruptedException {
        String key = getContext().get(EnvVars.class).expand(step.getKey());
        String keySuffix = createKeySuffix();

        return keySuffix == null ? new String[]{key} : new String[]{key + "-" + keySuffix, key};
    }

    @Override
    public boolean start() throws Exception {
        FilePath folder = new FilePath(new File(step.getFolder()));
        String[] restoreKeys = createRestoreKeys();

        cache.restore(folder, restoreKeys);

        getContext().newBodyInvoker().withCallback(new BodyExecutionCallback.TailCall() {
            @Override
            protected void finished(StepContext context) throws Exception {
                cache.backup(folder, restoreKeys[0]);
            }
        }).start();

        return false;
    }

}
