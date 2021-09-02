package io.jenkins.plugins.pipeline.cache.agent;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.pipeline.cache.Configuration;
import io.jenkins.plugins.pipeline.cache.S3OutputStream;

/**
 * Creates a tar archive of a given {@link FilePath} and uploads it to S3.
 */
public class BackupCallable extends AbstractMasterToAgentS3Callable {
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

        return new ResultBuilder()
                .withInfo("Cache saved successfully")
                .withInfo("Cache saved with key: " + key)
                .withInfo(format("Cache Size: %s B", s3().getObjectMetadata(config.getBucket(), key).getContentLength()))
                .build();
    }
}
