package io.jenkins.plugins.pipeline.cache.agent;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.pipeline.cache.Configuration;
import io.jenkins.plugins.pipeline.cache.S3OutputStream;

/**
 * Creates a tar archive of a given {@link FilePath} and uploads it to S3.
 */
public class BackupCallable extends AbstractMasterToAgentS3Callable {
    public static final long MB_TO_BYTES = 1000000;
    private final String key;

    public BackupCallable(Configuration config, String key) {
        super(config);
        this.key = key;
    }

    @Override
    public Result invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        if (s3().doesObjectExist(config.getBucket(), key)) {
            return new ResultBuilder()
                    .withInfo(format("Cache already exists (%s), not saving cache.", key))
                    .build();
        }

        try (S3OutputStream out = new S3OutputStream(s3(), config.getBucket(), key)) {
            new FilePath(f).tar(out, "**/*");
        }

        ObjectMetadata uploadedObjectMetadata = s3().getObjectMetadata(config.getBucket(), key);

        ResultBuilder result = new ResultBuilder();
        if (config.getSizeThresholdMb() > 0 &&
                uploadedObjectMetadata.getContentLength() > config.getSizeThresholdMb() * MB_TO_BYTES) {
            result.withInfo("WARNING: cache is larger than configured size threshold of "
                    + config.getSizeThresholdMb() + " MB," +
                    " at least one object is always cached despite that");
        }
        ResultBuilder resultBuilder = checkSizeThreshold(result, config.getBucket(), config.getSizeThresholdMb());

        return resultBuilder
                .withInfo("Cache saved successfully")
                .withInfo("Cache saved with key: " + key)
                .withInfo(format("Cache size: %s B", uploadedObjectMetadata.getContentLength()))
                .build();
    }

    public ResultBuilder checkSizeThreshold(ResultBuilder result, String bucket, long sizeThresholdMb) {
        long sizeThresholdBytes = sizeThresholdMb * 1000000;
        if (sizeThresholdMb <= 0) {
            return result.withInfo("Size threshold is less equal 0, will not delete any old objects");
        }
        List<S3ObjectSummary> s3ObjectSummaries = getS3ObjectSummaries(bucket);
        if (s3ObjectSummaries.isEmpty()) {
            // this should not happen though as @checkSizeThreshold is called after storing an object
            return result.withInfo("Cache is empty");
        }

        deleteOldObjects(result, s3ObjectSummaries, bucket, sizeThresholdBytes);
        return result;
    }

    private void deleteOldObjects(ResultBuilder result, List<S3ObjectSummary> s3ObjectSummaries, String bucket, long sizeThresholdBytes) {
        long bytesTotal = s3ObjectSummaries.stream().map(S3ObjectSummary::getSize).reduce(0L, Long::sum);
        if (bytesTotal > sizeThresholdBytes) {
            // sort to select oldest data
            s3ObjectSummaries.sort(Comparator.comparing(S3ObjectSummary::getLastModified));
            // refactor the following with stream().takeWhile() for Java 9+
            int deleteUpToIdx = 0;
            long toRemove = bytesTotal - sizeThresholdBytes;
            for (; deleteUpToIdx < s3ObjectSummaries.size() - 1 && toRemove > 0; deleteUpToIdx++) {
                toRemove -= s3ObjectSummaries.get(deleteUpToIdx).getSize();
                // remove the oldest objects to fit in with new object
                s3().deleteObject(bucket, s3ObjectSummaries.get(deleteUpToIdx).getKey());
            }
            result.withInfo("Cache storage exceeded size threshold, removed " + deleteUpToIdx + " item(s)");
        }
    }

    private List<S3ObjectSummary> getS3ObjectSummaries(String bucket) {
        ObjectListing objectListing = s3().listObjects(bucket);
        ArrayList<S3ObjectSummary> s3ObjectSummaries = new ArrayList<>(objectListing.getObjectSummaries());
        // handle pagination of objectListings
        while (objectListing.isTruncated()) {
            s3ObjectSummaries.addAll(objectListing.getObjectSummaries());
            objectListing = s3().listNextBatchOfObjects(objectListing);
        }
        return s3ObjectSummaries;
    }
}
