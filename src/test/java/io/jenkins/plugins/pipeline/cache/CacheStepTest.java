package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test with Jenkins and MinIO.
 */
public class CacheStepTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String bucket;

    @Before
    public void setup() throws IOException, InterruptedException {
        bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void testCacheNotExistsYet() throws Exception {
        // GIVEN
        Configuration.get().setUsername(minio.accessKey());
        Configuration.get().setPassword(minio.secretKey());
        Configuration.get().setBucket(bucket);
        Configuration.get().setRegion("us-west-1");
        Configuration.get().setEndpoint(minio.getExternalAddress());

        // GIVEN
        FileUtils.writeStringToFile(folder.newFile(), "some test data", StandardCharsets.UTF_8);

        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "echo env.JOB_NAME\n" +
                "sh 'echo bla > pom.xml'\n" +
                "echo 'before cache'\n" +
                "cache(folder: '"+folder.getRoot().getAbsolutePath()+"', hashFiles: '**/pom.xml', type: 'bla-foo') {\n" +
                "  echo 'within cache'\n" +
                "}\n" +
                "echo 'after cache'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache saved with key: bla-foo-3cd7a0db76ff9dca48979e24c39b408c", b);
    }

}
