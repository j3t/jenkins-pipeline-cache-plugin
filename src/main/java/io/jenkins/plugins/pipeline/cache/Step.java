package io.jenkins.plugins.pipeline.cache;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.TaskListener;

/**
 * Provides a cache step which can be used in pipelines to backup/restore a folder. The step tries to restore a the last backup and when
 * the step is completed the folder is stored in S3. The given key is assigned to the backup in S3 and if the hashFiles parameter is
 * present then the matching files are hashed and this hash is appended to the key.<br>
 * <br>
 * For example when the key is bla-foo and hashFiles is **&#47;pom.xml then all the pom.xml files in the workspace are hashed and the
 * resulting keys used to restore the folder are then like bla-foo-9a0364b9e99bb480dd25e1f0284c8555 and the second one is just bla-foo.
 * When the first key doesn't exist then the latest key starting with bla-foo is used instead.
 */
public class Step extends org.jenkinsci.plugins.workflow.steps.Step implements Serializable {

    private final String folder;
    private final String key;
    private String hashFiles;

    @DataBoundConstructor
    public Step(String folder, String key) {
        this.folder = folder;
        this.key = key;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new StepExecution(context, this);
    }

    public String getFolder() {
        return folder;
    }

    public String getKey() {
        return key;
    }

    public String getHashFiles() {
        return hashFiles;
    }

    @DataBoundSetter
    public void setHashFiles(String hashFiles) {
        this.hashFiles = hashFiles;
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "cache";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}