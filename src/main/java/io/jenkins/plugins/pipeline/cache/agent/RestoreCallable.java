package io.jenkins.plugins.pipeline.cache.agent;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.pipeline.cache.Configuration;

/**
 * Extracts an existing tar archive from S3 to a given {@link FilePath}.
 */
public class RestoreCallable extends AbstractMasterToAgentS3Callable {
    private final String[] restoreKeys;

    public RestoreCallable(Configuration config, String... restoreKeys) {
        super(config);
        this.restoreKeys = restoreKeys;
    }

    @Override
    public Result invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        String key = findKey();

        if (key == null) {
            return new ResultBuilder()
                    .withInfo("Cache not restored (no such key found)")
                    .build();
        }

        try (S3Object tar = s3().getObject(new GetObjectRequest(config.getBucket(), key));
             InputStream is = tar.getObjectContent()) {
            new FilePath(f).untarFrom(is, FilePath.TarCompression.NONE);
        }

        return new ResultBuilder()
                .withInfo("Cache restored successfully")
                .withInfo("Cache restored from key: "+key)
                .withInfo(format("Cache Size: %s B", s3().getObjectMetadata(config.getBucket(), key).getContentLength()))
                .build();
    }

    private String findKey() {
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
        if (s3().doesObjectExist(config.getBucket(), restoreKey)) {
            return restoreKey;
        }
        ObjectListing listing = s3().listObjects(config.getBucket(), restoreKey);

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
                listing = s3().listNextBatchOfObjects(listing);
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
