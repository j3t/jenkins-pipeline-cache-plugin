package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import hudson.FilePath;

public class Cache {

    private final String bucket;
    private final AmazonS3 s3;
    private final PrintStream logger;

    Cache(Configuration config, PrintStream logger) {
        this(config.getUsername(), config.getPassword(), config.getBucket(), config.getRegion(), config.getEndpoint(), logger);
    }

    Cache(String accessKey, String secretKey, String bucket, String region, String endpoint) throws UnsupportedEncodingException {
        this(accessKey, secretKey, bucket, region, endpoint, new PrintStream(System.out, false, "UTF-8"));
    }

    Cache(String accessKey, String secretKey, String bucket, String region, String endpoint, PrintStream logger) {
        this.bucket = bucket;
        this.logger = logger;
        s3 = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .build();
    }

    /**
     * Backup a given folder in S3. The folder is compressed with tar and then stored in S3 under the given key.
     *
     * @param folder folder which is used to collect the files (e.g. /home/alice/.m2/repository)
     * @param key    name of the key (e.g. my-project-maven or my-project-maven-c20ad4d76fe97759aa27a0c99bff6710)
     * @throws IOException          see {@link FilePath#tar(OutputStream, String)}
     * @throws InterruptedException see {@link FilePath#tar(OutputStream, String)}
     */
    public void backup(FilePath folder, String key) throws IOException, InterruptedException {
        if (s3.doesObjectExist(bucket, key)) {
            logger.printf("Cache already exists (%s), not saving cache.%n", key);
            return;
        }

        try (S3OutputStream out = new S3OutputStream(s3, bucket, key)) {
            folder.tar(out, "**/*");
        }

        logger.println("Cache saved successfully");
        logger.printf("Cache saved with key: %s%n", key);
        logger.printf("Cache Size: %s B%n", s3.getObjectMetadata(bucket, key).getContentLength());
    }

    /**
     * Restores a given folder from S3. The given restoreKeys are used to find an existing backup. You can define multiple restoreKeys
     * but only the best matching one will be restored. The keys are applied as given in the following order:
     * <ul>
     *     <li>the key which matches exactly the given key</li>
     *     <li>the key with the same prefix if there is only one</li>
     *     <li>the latest key with the same prefix if there are more</li>
     * </ul>
     *
     * @param folder      path to the folder where the backup should be restored (e.g. /home/alice/.m2/repository)
     * @param restoreKeys list of keys used to find the backup (e.g. my-project-maven-c20ad4d76fe97759aa27a0c99bff6710, my-project-maven)
     * @throws IOException          see {@link FilePath#untarFrom(InputStream, FilePath.TarCompression)}
     * @throws InterruptedException see {@link FilePath#untarFrom(InputStream, FilePath.TarCompression)}
     */
    public void restore(FilePath folder, String... restoreKeys) throws IOException, InterruptedException {
        String key = findKey(restoreKeys);

        if (key == null) {
            logger.println("Cache not restored (no such key found)");
            return;
        }

        try (S3Object tar = s3.getObject(new GetObjectRequest(bucket, key));
             InputStream is = tar.getObjectContent()) {
            folder.untarFrom(is, FilePath.TarCompression.NONE);
        }

        logger.println("Cache restored successfully");
        logger.printf("Cache restored from key: %s%n", key);
        logger.printf("Cache Size: %s B%n", s3.getObjectMetadata(bucket, key).getContentLength());
    }

    String findKey(String... restoreKeys) {
        if (restoreKeys == null) {
            return null;
        }

        String result = null;
        for (String restoreKey : restoreKeys) {
            result = findKeyByRestoreKey(restoreKey);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    private String findKeyByRestoreKey(String restoreKey) {
        if (restoreKey == null || restoreKey.isEmpty()) {
            return null;
        }

        // 1. key exists
        if (s3.doesObjectExist(bucket, restoreKey)) {
            return restoreKey;
        }
        ObjectListing listing = s3.listObjects(bucket, restoreKey);

        if (listing.getObjectSummaries().isEmpty()) {
            return null;
        }

        // 2. there is one key with the same prefix
        if (listing.getObjectSummaries().size() == 1) {
            return listing.getObjectSummaries().get(0).getKey();
        }

        // 3. there are more than one keys with the same prefix -> return the newest one
        Set<S3ObjectSummary> summaries = new HashSet<>();
        if (listing.isTruncated()) {
            while (listing.isTruncated()) {
                summaries.addAll(listing.getObjectSummaries());
                listing = s3.listNextBatchOfObjects(listing);
            }
        } else {
            summaries.addAll(listing.getObjectSummaries());
        }

        return summaries.stream()
                .sorted(Comparator.comparing(S3ObjectSummary::getLastModified).reversed())
                .map(S3ObjectSummary::getKey)
                .findFirst().orElse(null);
    }

}
