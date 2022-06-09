package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.util.UUID;

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

import hudson.model.Result;
import hudson.util.Secret;

/**
 * Checks that the cache step works as expected in pipelines. Each test starts with an empty bucket and the cache is also registered to
 * Jenkins.
 */
public class CacheStepTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    private String bucket;

    @Before
    public void setupJenkinsCache() throws IOException, InterruptedException {
        // GIVEN
        bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);

        // GIVEN
        CacheConfiguration.get().setUsername(minio.accessKey());
        CacheConfiguration.get().setPassword(Secret.fromString(minio.secretKey()));
        CacheConfiguration.get().setBucket(bucket);
        CacheConfiguration.get().setRegion("us-west-1");
        CacheConfiguration.get().setEndpoint(minio.getExternalAddress());
        CacheConfiguration.get().setThreshold(1);
    }

    @Test
    public void testBackupAndRestore() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: '1234') {\n" +
                "    sh 'echo my-content > "+folder.getRoot().getAbsolutePath()+"/test_file'\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (1234)", b);

        // WHEN
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: '1234') {\n" +
                "    sh 'cat "+folder.getRoot().getAbsolutePath()+"/test_file'\n" +
                "  }\n" +
                "}", true));
        b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (1234)", b);
        j.assertLogContains("my-content", b);
        j.assertLogContains("Cache not saved (1234 already exists)", b);
    }

    @Test
    public void testBackupIsSkippedOnError() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'a') {\n" +
                "    error 'Program failed, please read logs...'\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatus(Result.FAILURE, b);
        j.assertLogNotContains("Cache saved successfully (a)", b);
        j.assertLogContains("Cache not saved (inner-step execution failed)", b);
    }

    @Test
    public void testRestoreKey() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-a') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-b', restoreKeys: ['cache-a']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-a)", b);
        j.assertLogContains("Cache saved successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyNotFound() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-b', restoreKeys: ['cache-a']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyPrefix() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-a') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-b', restoreKeys: ['cac']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-a)", b);
        j.assertLogContains("Cache saved successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyFirstOneWins() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'a') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'b') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'c') {}\n" +
                "  cache(path: '" + folder.getRoot().getAbsolutePath() + "', key: 'd', restoreKeys: ['b','a','c']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (b)", b);
    }

    @Test
    public void testRestoreKeyExactMatchWins() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-a') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-b') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-c') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache', restoreKeys: ['cache-','cache-b']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyLatestOneWins() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-3') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-4') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-1') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-2') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-5', restoreKeys: ['cache-']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-2)", b);
    }

    @Test
    public void testRestoreKeyIgnored() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-a') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-b') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: 'cache-a', restoreKeys: ['cache-b']) {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-a)", b);
    }

    @Test
    public void testHashFiles() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'echo v1 > pom.xml'\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-4f98f59e877ecb84ff75ef0fab45bac5)", b);
    }

    @Test
    public void testHashFilesEmptyResult() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'touch workaround_workspace_not_exists'\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-d41d8cd98f00b204e9800998ecf8427e)", b);
    }

    @Test
    public void testPathNotExists() throws Exception {
        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"/empty', key: 'a') {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache not saved (path not exists)", b);
    }

    @Test
    public void testPathIsFile() throws Exception {
        // GIVEN
        folder.newFile("test_file");

        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"/test_file', key: 'a') {}\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (path is not a directory)", b);
        j.assertLogContains("Cache not saved (path is not a directory)", b);
    }

    @Test
    public void testPathFilter() throws Exception {
        // GIVEN
        folder.newFile("bla.json");
        folder.newFile("bla.xml");

        // WHEN
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"', filter: '**/*.json', key: 'a') {}\n" +
                "  cache(path: '"+folder.getRoot().getAbsolutePath()+"/restore', key: 'a') {\n" +
                "    sh 'test -f "+folder.getRoot().getAbsolutePath()+"/restore/bla.json && echo bla.json_exists || exit 0'\n" +
                "    sh 'test -f "+folder.getRoot().getAbsolutePath()+"/restore/bla.xml && echo bla.xml_exists || exit 0'\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("bla.json_exists", b);
        j.assertLogNotContains("bla.xml_exists", b);
    }
}
