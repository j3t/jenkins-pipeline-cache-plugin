package io.jenkins.plugins.pipeline.cache;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.UUID;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.util.Secret;
import hudson.util.StreamTaskListener;

/**
 * Checks that the cache eviction works as expected. Each test starts with an empty bucket.
 */
public class CacheCleanupTaskTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private String bucket;

    @Before
    public void setupJenkinsCache() {
        // GIVEN
        bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);

        // GIVEN
        CacheConfiguration config = CacheConfiguration.get();
        config.setUsername(minio.accessKey());
        config.setPassword(Secret.fromString(minio.secretKey()));
        config.setBucket(bucket);
        config.setRegion("us-west-1");
        config.setEndpoint(minio.getExternalAddress());
    }

    @Test
    public void testThresholdNotReached() {
        // GIVEN
        CacheConfiguration.get().setThreshold(2);
        String key = createCacheItem(1);

        // WHEN
        new CacheCleanupTask().execute(StreamTaskListener.fromStdout());

        // THEN
        assertThat(mc.containsKey(bucket, key), is(true));
    }

    @Test
    public void testThresholdReached() {
        // GIVEN
        CacheConfiguration.get().setThreshold(1);
        String key = createCacheItem(1);

        // WHEN
        new CacheCleanupTask().execute(StreamTaskListener.fromStdout());

        // THEN
        assertThat(mc.containsKey(bucket, key), is(false));
    }

    /**
     * Checks that cache items are removed in the order they are created (the oldest one first).
     */
    @Test
    public void testCacheItemsAreNotRestoredYet() {
        // GIVEN threshold: 5MB
        CacheConfiguration.get().setThreshold(5);

        // GIVEN 10 cache items (each ~1.1MB)
        List<String> keys = range(0, 10)
                .mapToObj(i -> createCacheItem(1))
                .collect(toList());

        // WHEN
        new CacheCleanupTask().execute(StreamTaskListener.fromStdout());

        // THEN expect the first 6 items are removed
        assertThat(range(0, 10).filter(i -> mc.containsKey(bucket, keys.get(i))).toArray(), is(new int[]{6, 7, 8, 9}));
    }

    private String createCacheItem(int sizeInMB) {
        try {
            return createCacheItemSecure(sizeInMB, UUID.randomUUID().toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    private String createCacheItemSecure(int sizeInMB, String key) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(path: '.', key: '" + key + "'){\n" +
                "    sh 'dd if=/dev/urandom of=file1 bs=1048576 count=" + sizeInMB + "'\n" +
                "  }\n" +
                "}", true));

        // WHEN
        WorkflowRun result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);
        j.assertBuildStatusSuccess(result);

        return key;
    }

}