package io.jenkins.plugins.pipeline.cache;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;

/**
 * Provides a pipeline step to cache files located on the build agent. Before the step gets executed, the files are restored from S3
 * and afterwards the state is sored in S3. See README.md for more details.
 */
public class CacheStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String folder;
    private final String hashFiles;
    private final String type;

    /**
     * @param folder absolute path to the folder you want to cache (e.g. $HOME/.m2/repository)
     * @param hashFiles glob pattern to filter files which have impact to the cache (e.g. **&#47;pom.xml)
     * @param type general name to identify the cache in general (e.g. my-project-maven)
     */
    @DataBoundConstructor
    public CacheStep(String folder, String hashFiles, String type) {
        this.folder = folder;
        this.hashFiles = hashFiles;
        this.type = type;
    }

    @Override
    public CacheStepExecution start(StepContext context) throws Exception {
        return new CacheStepExecution(context, this);
    }

    public String getFolder() {
        return folder;
    }

    public String getType() {
        return type;
    }

    public String getHashFiles() {
        return hashFiles;
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