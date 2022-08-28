package io.jenkins.plugins.pipeline.cache;

import java.io.PrintStream;
import java.io.Serializable;
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
 * Handles 'cache' step executions.<br><br>
 * How it works?
 * <ol>
 *     <li>cache gets restored by the given key to the given path (if key exists or one of the restoreKeys matches)</li>
 *     <li>inner-step gets executed</li>
 *     <li>backup of the path gets created (only if inner-step was successful and if the key not already exists)</li>
 * </ol>
 * Note: When a cache gets restored then a list of keys (key is the first one followed by the restoreKeys) is used to find a matching key
 * . See {@link io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository#findRestoreKey(String, String...)} for more details.
 * @deprecated replaced by the fileCache step (see {@link FileCacheStep})
 */
public class CacheStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * (required) Path to the folder (absolute or relative to the workspace) which should be cached (e.g. <i>$HOME/.m2/repository</i>).
     */
    private final String path;

    /**
     * (required) The unique identifier which is assigned to this cache (e.g. <i>maven-${hashFiles('**&#47;pom.xml')}</i>).
     */
    private final String key;

    /**
     * (optional) Additional keys which are used when the cache gets restored and the key doesn't exist (e.g. <i>maven-,
     * maven-4f98f59e877ecb84ff75ef0fab45bac5</i>).
     */
    @DataBoundSetter
    private String[] restoreKeys;

    /**
     * (optional) Glob pattern to filter the path (e.g. <i>**&#47;*.java</i> includes java files only).
     */
    @DataBoundSetter
    private String filter;

    @DataBoundConstructor
    public CacheStep(String path, String key) {
        this.path = path;
        this.key = key;
    }

    @Override
    public FileCacheStepExecution start(StepContext context) throws Exception {
        PrintStream logger = context.get(TaskListener.class).getLogger();
        logger.println("!!!!!! Warning !!!!!!");
        logger.println("The cache step is deprecated and will be removed in later releases!");
        logger.println("Please migrate your pipeline to the fileCache step (replace cache with fileCache).");
        logger.println("See https://github.com/j3t/jenkins-pipeline-cache-plugin/issues/20 for more details.");

        return new FileCacheStepExecution(context, path, key, restoreKeys, filter);
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