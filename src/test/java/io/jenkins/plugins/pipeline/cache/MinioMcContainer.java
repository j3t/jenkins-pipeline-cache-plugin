package io.jenkins.plugins.pipeline.cache;

import static java.lang.String.format;

import java.io.IOException;

import org.testcontainers.containers.GenericContainer;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class MinioMcContainer extends GenericContainer<MinioMcContainer> {

    private final MinioContainer minio;

    public MinioMcContainer(MinioContainer minio) {
        super("minio/mc");
        this.minio = minio;
        dependsOn(minio);
        withNetwork(minio.getNetwork());
        withCreateContainerCmdModifier(c -> c.withTty(true).withEntrypoint("/bin/sh"));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        execSecure("mc config host add test-minio http://%s:9000 %s %s",
                minio.getNetworkAliases().get(0),
                minio.accessKey(),
                minio.secretKey());
    }

    public ExecResult execSecure(String command, Object... args) {
        try {
            ExecResult result = exec(command, args);
            if (result.getExitCode() != 0) {
                throw new AssertionError(result.getStderr());
            }
            return result;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public ExecResult exec(String command, Object... args) throws IOException, InterruptedException {
        return execInContainer("/bin/sh", "-c",  format(command, args));
    }

    public void createBucket(String bucket) {
        execSecure("mc mb test-minio/%s", bucket);
    }

    public boolean containsKey(String bucket, String key) {
        try {
            return exec(format("mc stat test-minio/%s/%s", bucket, key)).getExitCode() == 0;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}