package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.io.PrintStream;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.cache.agent.BackupCallable;
import io.jenkins.plugins.pipeline.cache.agent.FileHashCallable;
import io.jenkins.plugins.pipeline.cache.agent.RestoreCallable;

/**
 * Executes pipeline step 'cache'.
 */
public class CacheStepExecution extends GeneralNonBlockingStepExecution {

    private static final long serialVersionUID = 1L;

    private final transient PrintStream logger;
    private final CacheStep step;
    private final Configuration config;

    public CacheStepExecution(StepContext context, CacheStep step) throws IOException, InterruptedException {
        super(context);
        this.step = step;
        this.logger = context.get(TaskListener.class).getLogger();
        this.config = Configuration.get();
    }

    @Override
    public boolean start() throws Exception {
        // get workspace (might be located on a remote machine)
        FilePath workspace = getContext().get(FilePath.class);
        // we have to locate the folder via the workspace in order to get access to the agents file system
        FilePath folder = workspace.child(step.getFolder());
        String type = getContext().get(EnvVars.class).expand(step.getType());

        // hash workspace files and create restore keys
        String hash = workspace.act(new FileHashCallable(step.getHashFiles()));
        String[] restoreKeys = new String[]{type + "-" + hash, type};

        // restore folder
        folder.act(new RestoreCallable(config, restoreKeys)).printInfos(logger);

        // execute inner-step and backup folder after completion
        getContext().newBodyInvoker().withCallback(new BodyExecutionCallback.TailCall() {
            @Override
            protected void finished(StepContext context) throws Exception {
                folder.act(new BackupCallable(config, restoreKeys[0])).printInfos(logger);
            }
        }).start();

        return false;
    }

}
