package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
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

import hudson.model.Result;
import hudson.util.Secret;

/**
 * Checks that the 'fileCache' step works as expected in pipelines. Each test starts with an empty bucket and the cache is also
 * registered to Jenkins.
 */
public class FileCacheStepTest {

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
    public void testBackupAndRestore() throws Exception {
        // GIVEN
        WorkflowJob p1 = createWorkflow("node {\n" +
                "  fileCache(path: '.', key: '1234') {\n" +
                "    sh 'echo expected-content > file'\n" +
                "  }\n" +
                "}");
        WorkflowJob p2 = createWorkflow("node {\n" +
                "  fileCache(path: '.', key: '1234') {\n" +
                "    sh 'cat file'\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun b1 = executeWorkflow(p1);
        WorkflowRun b2 = executeWorkflow(p2);

        // THEN
        j.assertBuildStatusSuccess(b1);
        j.assertLogContains("Cache not restored (no such key found)", b1);
        j.assertLogContains("Cache saved successfully (1234)", b1);
        j.assertBuildStatusSuccess(b2);
        j.assertLogContains("Cache restored successfully (1234)", b2);
        j.assertLogContains("expected-content", b2);
        j.assertLogContains("Cache not saved (1234 already exists)", b2);
    }

    @Test
    public void testBackupIsSkippedOnError() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  fileCache(path: '.', key: 'a') {\n" +
                "    error 'Program failed, please read logs...'\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatus(Result.FAILURE, b);
        j.assertLogNotContains("Cache saved successfully (a)", b);
        j.assertLogContains("Cache not saved (inner-step execution failed)", b);
    }

    @Test
    public void testBackupAndRestoreMoreFiles() throws Exception {
        // GIVEN
        WorkflowJob p1 = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b'\n" +
                "  sh 'dd if=/dev/urandom of=a/f1 bs=1048576 count=1'\n" +
                "  sh 'dd if=/dev/urandom of=a/f2 bs=1048576 count=2'\n" +
                "  sh 'dd if=/dev/urandom of=a/f3 bs=1048576 count=3'\n" +
                "  sh 'dd if=/dev/urandom of=a/f4 bs=1048576 count=4'\n" +
                "  sh 'dd if=/dev/urandom of=a/f5 bs=1048576 count=5'\n" +
                "  fileCache(path: 'a', key: 'a') {}\n" +
                "  fileCache(path: 'b', key: 'a') {}\n" +
                "  assert sha256('a/f1') == sha256('b/f1')\n" +
                "  assert sha256('a/f2') == sha256('b/f2')\n" +
                "  assert sha256('a/f3') == sha256('b/f3')\n" +
                "  assert sha256('a/f4') == sha256('b/f4')\n" +
                "  assert sha256('a/f5') == sha256('b/f5')\n" +
                "}");

        // WHEN
        WorkflowRun b1 = executeWorkflow(p1);

        // THEN
        j.assertBuildStatus(Result.SUCCESS, b1);
    }

    @Test
    public void testRestoreKey() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b'\n" +
                "  fileCache(path: 'a', key: 'cache-a') {}\n" +
                "  fileCache(path: 'b', key: 'cache-b', restoreKeys: ['cache-a']) {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache saved successfully (cache-a)", b);
        j.assertLogContains("Cache restored successfully (cache-a)", b);
        j.assertLogContains("Cache saved successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyNotFound() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  fileCache(path: '.', key: 'cache-b', restoreKeys: ['cache-a']) {\n" +
                "    sh 'touch file'\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyPrefix() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b'\n" +
                "  fileCache(path: 'a', key: 'cache-a') {}\n" +
                "  fileCache(path: 'b', key: 'cache-b', restoreKeys: ['cac']) {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-a)", b);
        j.assertLogContains("Cache saved successfully (cache-b)", b);
    }

    @Test
    public void testRestoreKeyFirstOneWins() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b && mkdir c && mkdir d'\n" +
                "  fileCache(path: 'a', key: 'a') {}\n" +
                "  fileCache(path: 'b', key: 'b') {}\n" +
                "  fileCache(path: 'c', key: 'c') {}\n" +
                "  fileCache(path: 'd', key: 'd', restoreKeys: ['b','a','c']) {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (b)", b);
    }

    @Test
    public void testRestoreKeyExactMatchWins() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b && mkdir c && mkdir d'\n" +
                "  fileCache(path: 'a', key: 'cache-a') {}\n" +
                "  fileCache(path: 'b', key: 'cache-b') {}\n" +
                "  fileCache(path: 'c', key: 'cache-c') {}\n" +
                "  fileCache(path: 'd', key: 'cache', restoreKeys: ['cache-','cache-b']) {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-b)", b);
    }

    @Test
    public void testRestoreLatestOne() throws Exception {
        // GIVEN
        WorkflowJob p1 = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b && mkdir c && mkdir d'\n" +
                "  fileCache(path: 'a', key: 'cache-1') {}\n" +
                "  fileCache(path: 'b', key: 'cache-2') {}\n" +
                "  fileCache(path: 'c', key: 'cache-3') {}\n" +
                "  fileCache(path: 'd', key: 'cache-2') {}\n" +
                "}");
        WorkflowJob p2 = createWorkflow("node {\n" +
                "  fileCache(path: '.', key: 'cache-4', restoreKeys: ['cache-']) {}\n" +
                "}");

        // WHEN
        WorkflowRun b1 = executeWorkflow(p1);
        WorkflowRun b2 = executeWorkflow(p2);

        // THEN
        j.assertBuildStatusSuccess(b1);
        j.assertBuildStatusSuccess(b2);
        j.assertLogContains("Cache restored successfully (cache-3)", b2);
    }

    @Test
    public void testRestoreKeyIgnored() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a && mkdir b && mkdir c'\n" +
                "  fileCache(path: 'a', key: 'cache-a') {}\n" +
                "  fileCache(path: 'b', key: 'cache-b') {}\n" +
                "  fileCache(path: 'c', key: 'cache-a', restoreKeys: ['cache-b']) {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache restored successfully (cache-a)", b);
    }

    @Test
    public void testHashFilesMavenProject() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'echo v1 > pom.xml'\n" +
                "  sh 'dd if=/dev/urandom of=file1 bs=1024 count=8'\n" +
                "  fileCache(path: '.', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-4f98f59e877ecb84ff75ef0fab45bac5)", b);
    }

    @Test
    public void testHashFilesMultiMavenProject() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'echo v1 > pom.xml'\n" +
                "  sh 'mkdir sub && echo v2 > sub/pom.xml'\n" +
                "  sh 'dd if=/dev/urandom of=file1 bs=1024 count=8'\n" +
                "  fileCache(path: '.', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-c80a4fa9fc4e0041feb5240f35a74105)", b);
    }

    @Test
    public void testHashFilesEmptyResult() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a'\n" +
                "  fileCache(path: 'a', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache saved successfully (cache-d41d8cd98f00b204e9800998ecf8427e)", b);
    }

    @Test
    public void testPathNotExists() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  fileCache(path: 'not-exists', key: 'a') {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (no such key found)", b);
        j.assertLogContains("Cache not saved (path not exists)", b);
    }

    @Test
    public void testPathIsFile() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'touch is-file'\n" +
                "  fileCache(path: 'is-file', key: 'a') {}\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
        j.assertLogContains("Cache not restored (path is not a directory)", b);
        j.assertLogContains("Cache not saved (path is not a directory)", b);
    }

    @Test
    public void testFilter() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir a && touch a/file1.xml && touch a/file2.xml'\n" +
                "  fileCache(path: 'a', filter: '**/file1.xml', key: 'a') {}\n" +
                "  fileCache(path: 'b', key: 'a') {}\n" +
                "  assert fileExists('b/file1.xml')\n" +
                "  assert !fileExists('b/file2.xml')\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
    }

    @Test
    public void testDefaultExcludes() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir -p a/.git && touch a/.git/config'\n" +
                "  fileCache(path: 'a', key: 'a') {}\n" +
                "  fileCache(path: 'b', key: 'a') {}\n" +
                "  assert !fileExists('b/.git/config')\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
    }

    @Test
    public void testDefaultExcludesIgnoredWhenFilterIsActive() throws Exception {
        // GIVEN
        WorkflowJob p = createWorkflow("node {\n" +
                "  sh 'mkdir -p a/.git && touch a/.git/config'\n" +
                "  fileCache(path: 'a', key: 'a', filter: '**/*') {}\n" +
                "  fileCache(path: 'b', key: 'a') {}\n" +
                "  assert fileExists('b/.git/config')\n" +
                "}");

        // WHEN
        WorkflowRun b = executeWorkflow(p);

        // THEN
        j.assertBuildStatusSuccess(b);
    }

    private WorkflowJob createWorkflow(String script) throws IOException {
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(script, true));
        return p;
    }

    private WorkflowRun executeWorkflow(WorkflowJob p) throws Exception {
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);
        return b;
    }
}
