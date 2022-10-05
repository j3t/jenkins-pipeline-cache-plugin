package io.jenkins.plugins.pipeline.cache;

import java.util.UUID;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.util.Secret;

/**
 * Checks that the cache step is still working but deprecated.
 * @deprecated will be removed when the cache step is removed completely
 */
public class CacheStepTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @BeforeClass
    public static void setupExecutor() throws Exception {
        // execute build jobs on a dedicated agent node
        j.jenkins.setNumExecutors(0);
        j.createSlave(true);
    }

    @Before
    public void setupCache() {
        // GIVEN
        String bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);

        // GIVEN
        CacheConfiguration config = CacheConfiguration.get();
        config.setUsername(minio.accessKey());
        config.setPassword(Secret.fromString(minio.secretKey()));
        config.setBucket(bucket);
        config.setRegion("us-west-1");
        config.setEndpoint(minio.getExternalAddress());
        config.setThreshold(0);
    }

    @Test
    public void testCacheStepIsStillAvailableButDeprecated() throws Exception {
        // GIVEN
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '.', key: '1234') {\n" +
                "    sh 'echo expected-content > file'\n" +
                "  }\n" +
                "}", true));

        // WHEN
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (1234)", b);
        j.assertLogContains("!!!!!! Warning !!!!!!", b);
        j.assertLogContains("The cache step is deprecated and will be removed in later releases!", b);
        j.assertLogContains("Please migrate your pipeline to the fileCache step (replace cache with fileCache).", b);
        j.assertLogContains("See https://github.com/j3t/jenkins-pipeline-cache-plugin/issues/20 for more details.", b);
    }

}
