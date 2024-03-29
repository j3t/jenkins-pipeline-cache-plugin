package io.jenkins.plugins.pipeline.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
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
import jenkins.MasterToSlaveFileCallable;

/**
 * Handles 'hashFiles' step executions. For example, <b>hashFiles('**&#47;pom.xml')</b> will create a hash over all pom files within the
 * working directory. If there are no matching files, then d41d8cd98f00b204e9800998ecf8427e is returned (md5 of an empty string).
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
            return "Hash files within the working directory";
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
            FilePath workdir = getContext().get(FilePath.class);

            return workdir.act(new HashFilesStepExecution.HashFilesCallable(pattern));
        }

        private static class HashFilesCallable extends MasterToSlaveFileCallable<String> {

            private final String pattern;

            private HashFilesCallable(String pattern) {
                this.pattern = pattern;
            }

            @Override
            public String invoke(File workdir, VirtualChannel channel) throws IOException {
                PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                MessageDigest checksum = DigestUtils.getMd5Digest();

                try (Stream<Path> files = Files.walk(Paths.get(workdir.toURI()))) {
                    files
                            .filter(filter::matches)
                            .sorted()
                            .forEach(path -> updateChecksum(checksum, path));

                    return Hex.encodeHexString(checksum.digest());
                }
            }

            private void updateChecksum(MessageDigest checksum, Path path) {
                try {
                    DigestUtils.updateDigest(checksum, path);
                } catch (IOException e) {
                    throw new IllegalStateException("Update checksum has been failed!", e);
                }
            }
        }
    }

}
