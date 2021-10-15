package io.jenkins.plugins.pipeline.cache;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.jenkins.plugins.pipeline.cache.agent.AbstractMasterToAgentS3Callable;
import io.jenkins.plugins.pipeline.cache.agent.BackupCallable;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Integration test with Jenkins and MinIO.
 */
public class CacheStepSizeThresholdTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String bucket;
    private AmazonS3 client = null;
    private BackupCallable backupCallable;


    @Before
    public void setup() throws IOException, InterruptedException {
        bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);
        String username = minio.accessKey();
        String password = minio.secretKey();
        String region = "us-west-1";
        String endpoint = minio.getExternalAddress();
        client = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(username, password)))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .build();

        backupCallable = Mockito.spy(new BackupCallable(null, null));
        Mockito.doReturn(client).when(backupCallable).s3();
    }

    @After
    public void shutDown() {
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }

    @Test
    public void testDoesntDeleteBelowThreshold() throws Exception {
        // GIVEN
        AbstractMasterToAgentS3Callable.ResultBuilder resultBuilder = new AbstractMasterToAgentS3Callable.ResultBuilder();

        for (int i = 0; i < 10; i++) {
            // GIVEN
            mc.createObject(bucket, "asdf" + i, "asdf");

            // WHEN
            backupCallable.checkSizeThreshold(resultBuilder, bucket, 1);

            for (int j = 0; j <= i; j++) {
                // THEN
                assertTrue(client.doesObjectExist(bucket, "asdf" + i));

            }
        }
    }

    @Test
    public void testDeleteBelowThreshold() throws Exception {
        // GIVEN
        AbstractMasterToAgentS3Callable.ResultBuilder resultBuilder = new AbstractMasterToAgentS3Callable.ResultBuilder();
        mc.createObject(bucket, "asdf1", 2);

        // WHEN
        backupCallable.checkSizeThreshold(resultBuilder, bucket, 1);

        // THEN
        assertTrue(client.doesObjectExist(bucket, "asdf1"));

        // GIVEN
        mc.createObject(bucket, "asdf2", 2);

        // WHEN
        backupCallable.checkSizeThreshold(resultBuilder, bucket, 1);

        // THEN
        assertFalse(client.doesObjectExist(bucket, "asdf1"));
        assertTrue(client.doesObjectExist(bucket, "asdf2"));
    }

    @Test
    public void testThresholdZero() throws Exception {
        // GIVEN
        mc.createObject(bucket, "asdf1", 1);
        mc.createObject(bucket, "asdf2", 1);
        mc.createObject(bucket, "asdf3", 1);
        mc.createObject(bucket, "asdf4", 1);

        AbstractMasterToAgentS3Callable.ResultBuilder resultBuilder = new AbstractMasterToAgentS3Callable.ResultBuilder();

        // WHEN
        backupCallable.checkSizeThreshold(resultBuilder, bucket, 0);

        // THEN
        assertTrue(client.doesObjectExist(bucket, "asdf1"));
        assertTrue(client.doesObjectExist(bucket, "asdf2"));
        assertTrue(client.doesObjectExist(bucket, "asdf3"));
        assertTrue(client.doesObjectExist(bucket, "asdf4"));
    }

}
