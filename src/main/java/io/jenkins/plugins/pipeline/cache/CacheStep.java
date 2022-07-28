package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.cache.agent.BackupCallable;
import io.jenkins.plugins.pipeline.cache.agent.RestoreCallable;

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
    public CacheStepExecution start(StepContext context) throws Exception {
        return new CacheStepExecution(context, this);
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

    private static class CacheStepExecution extends GeneralNonBlockingStepExecution {

        private static final long serialVersionUID = 1L;

        private final transient PrintStream logger;
        private final CacheStep step;
        private final CacheConfiguration config;

        public CacheStepExecution(StepContext context, CacheStep step) throws IOException, InterruptedException {
            super(context);
            this.step = step;
            this.logger = context.get(TaskListener.class).getLogger();
            this.config = CacheConfiguration.get();
        }

        @Override
        public boolean start() throws Exception {
            FilePath workspace = getContext().get(FilePath.class);
            FilePath path = workspace.child(step.path);

            // restore existing cache
            path.act(new RestoreCallable(config, step.key, step.restoreKeys)).printInfos(logger);

            // execute inner-step and save cache afterwards
            getContext().newBodyInvoker().withCallback(new BodyExecutionCallback() {
                @Override
                public void onSuccess(StepContext context, Object result) {
                    try {
                        path.act(new BackupCallable(config, step.key, step.filter)).printInfos(logger);
                    } catch (Exception x) {
                        context.onFailure(x);
                        return;
                    }
                    context.onSuccess(result);
                }

                @Override
                public void onFailure(StepContext context, Throwable t) {
                    logger.println("Cache not saved (inner-step execution failed)");
                    context.onFailure(t);
                }
            }).start();

            return false;
        }

    }

}