package io.jenkins.plugins.pipeline.cache;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.TaskListener;

/**
 * Provides a pipeline step to cache files located on the build agent. Before the step gets executed, the files are restored from S3
 * and afterwards the state is sored in S3. See README.md for more details.
 */
public class CacheStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String path;
    private final String key;

    private String[] restoreKeys;
    private String filter;

    /**
     * @param path Absolute path to the folder you want to cache (e.g. $HOME/.m2/repository)
     * @param key Identifier assigned to this cache (e.g. maven-${hashFiles('**&#47;pom.xml')})
     */
    @DataBoundConstructor
    public CacheStep(String path, String key) {
        this.path = path;
        this.key = key;
    }

    @Override
    public CacheStepExecution start(StepContext context) throws Exception {
        return new CacheStepExecution(context, this);
    }

    public String getPath() {
        return path;
    }

    public String getKey() {
        return key;
    }

    public String[] getRestoreKeys() {
        return restoreKeys == null ? null : Arrays.copyOf(restoreKeys, restoreKeys.length);
    }

    /**
     * @param restoreKeys Additional keys used to restore the Cache (e.g. maven-, maven-4f98f59e877ecb84ff75ef0fab45bac5)
     */
    @DataBoundSetter
    public void setRestoreKeys(String[] restoreKeys) {
        this.restoreKeys = restoreKeys == null ? null : Arrays.copyOf(restoreKeys, restoreKeys.length);
    }

    public String getFilter() {
        return filter;
    }

    /**
     * @param filter Ant file pattern mask, like <b>**&#47;*.java</b> which is applied to the path.
     */
    @DataBoundSetter
    public void setFilter(String filter) {
        this.filter = filter;
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