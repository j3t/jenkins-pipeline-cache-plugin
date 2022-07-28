package io.jenkins.plugins.pipeline.cache.s3;

import static java.util.stream.Stream.concat;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class CacheItemRepository {

    static final String LAST_ACCESS = "LAST_ACCESS";
    static final String CREATION = "CREATION";

    private final AmazonS3 s3;
    private final String bucket;

    public CacheItemRepository(String username, String password, String region, String endpoint, String bucket) {
        this.s3 = createS3Client(username, password, endpoint, region);
        this.bucket = bucket;
    }

    protected AmazonS3 createS3Client(String username, String password, String endpoint, String region) {
        return AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(username, password)))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .build();
    }

    /**
     * Provides the total size of all cache items.
     */
    public long getTotalCacheSize() {
        return Stream.of(s3.listObjects(bucket))
                .flatMap(this::flatMapObjectSummaries)
                .map(S3ObjectSummary::getSize)
                .reduce(Long::sum)
                .orElse(0L);
    }

    /**
     * Provides a stream of all cache items.
     */
    public Stream<CacheItem> findAll() {
        return flatMapObjectSummaries(s3.listObjects(bucket)).map(this::mapToCacheItem);
    }

    /**
     * Removes items from the cache.
     * @param keys Stream of keys which should be removed
     * @return count of removed items
     */
    public int delete(Stream<String> keys) {
        return s3.deleteObjects(new DeleteObjectsRequest(bucket)
                .withKeys(keys.toArray(String[]::new))
        ).getDeletedObjects()
                .size();
    }

    /**
     * Provides the size of a cache item in byte.
     */
    public long getContentLength(String key) {
        return s3.getObjectMetadata(bucket, key).getContentLength();
    }

    /**
     * Provides the {@link S3Object} assigned to a given key or null if it not exists.
     */
    public S3Object getS3Object(String key) {
        return s3.getObject(new GetObjectRequest(bucket, key));
    }

    /**
     * Updates the last access timestamp of a given cache item by key. <b>Note: As a side effect this also changes the last modification
     * timestamp, which means that last modification and last access can be considered as equals/b>
     */
    public void updateLastAccess(String key) {
        ObjectMetadata metadata = s3.getObjectMetadata(bucket, key);
        metadata.addUserMetadata(LAST_ACCESS, Long.toString(System.currentTimeMillis()));

        // HACK: the only way to change the metadata of an existing object is to create a copy to itself
        s3.copyObject(new CopyObjectRequest(bucket, key, bucket, key).withNewObjectMetadata(metadata));
    }

    /**
     * Finds the best matching key which can be used to restore an existing cache. It works as follows:
     * <ol>
     *   <li>if key exists then key is returned</li>
     *   <li>if one of the restoreKeys exists then this one is returned</li>
     *   <li>if an existing key starts with one of the restoreKeys then the existing key is returned</li>
     *   <li>otherwise null is returned</li>
     * </ol>
     */
    public String findRestoreKey(String key, String... restoreKeys) {
        if (key != null && exists(key)) {
            // 1.
            return key;
        }

        if (restoreKeys == null) {
            // 4.
            return null;
        }

        for (String restoreKey : restoreKeys) {
            if (exists(restoreKey)) {
                // 2.
                return restoreKey;
            }
        }

        return Arrays.stream(restoreKeys)
                .map(this::findKeyByPrefix)
                .filter(Objects::nonNull)
                // 3.
                .findFirst()
                // 4.
                .orElse(null);
    }

    /**
     * Returns true if the object with the given exists, otherwise false.
     */
    public boolean exists(String key) {
        return s3.doesObjectExist(bucket, key);
    }

    /**
     * Creates an {@link java.io.OutputStream} for a given key. This can be used to write data directly to a new object in S3.
     */
    public OutputStream createObjectOutputStream(String key) {
        return new S3OutputStream(s3, bucket, key);
    }

    /**
     * Returns true if the underlying bucket exists, otherwise false.
     */
    public boolean bucketExists() {
        try {
            s3.headBucket(new HeadBucketRequest(bucket));
            return true;
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Collects the {@link S3ObjectSummary}s from a given {@link ObjectListing} and returns them as a {@link Stream}. If the
     * {@link ObjectListing} is truncated (one batch of many) then the truncated ones are resolved and added to the {@link Stream} as well.
     */
    private Stream<S3ObjectSummary> flatMapObjectSummaries(ObjectListing listing) {
        Stream<S3ObjectSummary> result = listing.getObjectSummaries().stream();

        return listing.isTruncated() ? concat(result, flatMapObjectSummaries(s3.listNextBatchOfObjects(listing))) : result;
    }

    /**
     * Transforms a {@link S3ObjectSummary} object into a {@link CacheItem} object.
     */
    private CacheItem mapToCacheItem(S3ObjectSummary s3ObjectSummary) {
        return new CacheItem(
                s3ObjectSummary.getKey(),
                s3ObjectSummary.getSize(),
                // we just use the last modified timestamp here as last access time (assumption: last access and last modified are equals
                // anyway), this saves one extra request (last access timestamp is stored as metadata)
                s3ObjectSummary.getLastModified().getTime()
        );
    }

    private String findKeyByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }

        ObjectListing listing = s3.listObjects(bucket, prefix);

        // 1. no key with the same prefix exists
        if (listing.getObjectSummaries().isEmpty()) {
            return null;
        }

        // 2. one key with the same prefix exists
        if (listing.getObjectSummaries().size() == 1) {
            return listing.getObjectSummaries().get(0).getKey();
        }

        // 3. more than one key with the same prefix exists -> return the latest one
        return flatMapObjectSummaries(listing)
                .map(this::mapToKeyCreation)
                .max(Comparator.comparing(KeyCreation::getCreation))
                .map(KeyCreation::getKey)
                .orElse(null);
    }

    private KeyCreation mapToKeyCreation(S3ObjectSummary s3ObjectSummary) {
        ObjectMetadata m = s3.getObjectMetadata(bucket, s3ObjectSummary.getKey());

        return new KeyCreation(
                s3ObjectSummary.getKey(),
                Long.parseLong(m.getUserMetadata().getOrDefault(CREATION, "0"))
        );
    }

    private static class KeyCreation {
        private final String key;
        private final long creation;

        private KeyCreation(String key, long creation) {
            this.key = key;
            this.creation = creation;
        }

        public String getKey() {
            return key;
        }

        public long getCreation() {
            return creation;
        }
    }
}
