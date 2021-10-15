package io.jenkins.plugins.pipeline.cache;

import java.io.File;
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
    public void testBackupAndRestore() throws Exception {
        // GIVEN
        Configuration.get().setUsername(minio.accessKey());
        Configuration.get().setPassword(minio.secretKey());
        Configuration.get().setBucket(bucket);
        Configuration.get().setRegion("us-west-1");
        Configuration.get().setEndpoint(minio.getExternalAddress());

        // GIVEN
        File file = folder.newFile();
        FileUtils.writeStringToFile(file, "some test data", StandardCharsets.UTF_8);

        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'echo bla > pom.xml'\n" +
                "  cache(folder: '"+folder.getRoot().getAbsolutePath()+"', hashFiles: '**/pom.xml', type: 'bla-foo') {\n" +
                "    sh 'cat "+file.getAbsolutePath()+"'\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("some test data", b);
        j.assertLogContains("Cache saved with key: bla-foo-3cd7a0db76ff9dca48979e24c39b408c", b);

        // WHEN
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'rm "+file.getAbsolutePath()+"'\n" +
                "  cache(folder: '"+folder.getRoot().getAbsolutePath()+"', hashFiles: '**/pom.xml', type: 'bla-foo') {\n" +
                "    sh 'cat "+file.getAbsolutePath()+"'\n" +
                "  }\n" +
                "}", true));
        b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored from key: bla-foo-3cd7a0db76ff9dca48979e24c39b408c", b);
        j.assertLogContains("some test data", b);
        j.assertLogContains("Cache already exists (bla-foo-3cd7a0db76ff9dca48979e24c39b408c), not saving cache.", b);
    }

    @Test
    public void testSizeThreshold() throws Exception {
        // GIVEN
        Configuration.get().setUsername(minio.accessKey());
        Configuration.get().setPassword(minio.secretKey());
        Configuration.get().setBucket(bucket);
        Configuration.get().setRegion("us-west-1");
        Configuration.get().setEndpoint(minio.getExternalAddress());
        Configuration.get().setSizeThresholdMb(1);

        // GIVEN
        File file = folder.newFile();
        FileUtils.writeStringToFile(file, "some test data", StandardCharsets.UTF_8);

        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'echo bla > pom.xml'\n" +
                "  sh 'dd if=/dev/urandom of="+file.getAbsolutePath()+" bs=1048576 count=2'\n" +
                "  cache(folder: '"+folder.getRoot().getAbsolutePath()+"', hashFiles: '**/pom.xml', type: 'bla-foo') {\n" +
                "    sh 'ls -alth'\n" +
                "    sh 'ls -alth "+folder.getRoot().getAbsolutePath()+"'\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache size: 209", b); // how to match with regex for whole line?
        j.assertLogContains("WARNING: cache is larger than configured size threshold of 1 MB", b);

        // WHEN
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'rm "+file.getAbsolutePath()+"'\n" +
                "  cache(folder: '"+folder.getRoot().getAbsolutePath()+"', hashFiles: '**/pom.xml', type: 'bla-foo') {\n" +
                "    sh 'ls -alth'\n" +
                "    sh 'ls -alth "+folder.getRoot().getAbsolutePath()+"'\n" +
                "  }\n" +
                "}", true));
        b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored from key: bla-foo-3cd7a0db76ff9dca48979e24c39b408c", b);
        // Checks that the downloaded cache is bigger than threshold.
        // It makes sure that the threshold can be exceeded and the file was stored.
        j.assertLogContains("total 2.1M", b);
        j.assertLogContains("Cache already exists (bla-foo-3cd7a0db76ff9dca48979e24c39b408c), not saving cache.", b);

        // GIVEN
        File pomfile = folder.newFile("pom.xml");
        FileUtils.writeStringToFile(pomfile, "other", StandardCharsets.UTF_8);

        // WHEN
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'echo other > pom.xml'\n" +
                "  sh 'dd if=/dev/urandom of="+file.getAbsolutePath()+" bs=1048576 count=1'\n" +
                "  cache(folder: '"+folder.getRoot().getAbsolutePath()+"', hashFiles: '**/pom.xml', type: 'other-foo') {\n" +
                "    sh 'ls -alth'\n" +
                "    sh 'ls -alth "+folder.getRoot().getAbsolutePath()+"'\n" +
                "  }\n" +
                "}", true));
        b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        // Checks that file from a different project was removed so that the new 1 MB file can be stored
        j.assertLogContains("Cache storage exceeded size threshold, removed 1 item(s)", b);
    }
}
