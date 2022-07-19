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
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.IteratorUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.SlaveToMasterFileCallable;

/**
 * Handles 'hashFiles' step executions. For example, <b>hashFiles('**&#47;pom.xml')</b> will create a hash over all pom files in the
 * workspace. If there are no matching files at all then d41d8cd98f00b204e9800998ecf8427e is returned (md5 hash of an empty string).
 */
public class HashFilesStep extends Step {

    private final String pattern;

    /**
     * @param pattern Glob pattern to filter the workspace (e.g. **&#47;pom.xml would include only maven pom files)
     */
    @DataBoundConstructor
    public HashFilesStep(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new HashFilesStepExecution(context, pattern);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class);
        }

        @Override
        public String getFunctionName() {
            return "hashFiles";
        }

        @Override
        public String getDisplayName() {
            return "Hashes files in the workspace";
        }
    }

    private static class HashFilesStepExecution extends SynchronousNonBlockingStepExecution<String> {
        private final String pattern;

        public HashFilesStepExecution(StepContext context, String pattern) {
            super(context);
            this.pattern = pattern;
        }

        @Override
        protected String run() throws Exception {
            FilePath workspace = getContext().get(FilePath.class);

            return workspace.act(new HashFilesStepExecution.HashFilesCallable(pattern));
        }

        private static class HashFilesCallable extends SlaveToMasterFileCallable<String> {

            private final String pattern;

            private HashFilesCallable(String pattern) {
                this.pattern = pattern;
            }

            @Override
            public String invoke(File f, VirtualChannel channel) throws IOException {
                PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                Stream<FileInputStream> streams = Files.walk(Paths.get(f.toURI()))
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
    }

}
