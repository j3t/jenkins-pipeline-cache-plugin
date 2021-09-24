package io.jenkins.plugins.pipeline.cache.agent;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import io.jenkins.plugins.pipeline.cache.Configuration;
import jenkins.MasterToSlaveFileCallable;

/**
 * Base class for S3 related operations. Note: Plugin code is executed on the master node but the files we want to cache are located on the
 * build agent. In order to process the files on the agent, we use {@link MasterToSlaveFileCallable}.
 */
public abstract class AbstractMasterToAgentS3Callable extends MasterToSlaveFileCallable<AbstractMasterToAgentS3Callable.Result> {

    final Configuration config;
    private volatile AmazonS3 client;

    protected AbstractMasterToAgentS3Callable(Configuration config) {
        this.config = config;
    }

    /**
     * Provides a {@link AmazonS3} instance which can used on the agent directly.
     */
    public AmazonS3 s3() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AmazonS3ClientBuilder
                            .standard()
                            .withPathStyleAccessEnabled(true)
                            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(config.getUsername(), config.getPassword())))
                            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(config.getEndpoint(), config.getRegion()))
                            .build();
                }
            }
        }
        return client;
    }

    public static class ResultBuilder {

        private Result result = new Result();

        /**
         * Creates a new Result object.
         */
        public Result build() {
            Result build = new Result();
            build.infos = new ArrayList<>(result.infos);
            return build;
        }

        /**
         * Adds a given info message to the result.
         */
        public ResultBuilder withInfo(String s) {
            result.addInfo(s);
            return this;
        }
    }

    /**
     * Result object.
     */
    public static class Result implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<String> infos = new ArrayList<>();

        /**
         * Adds a given info message to the result.
         */
        public void addInfo(String s) {
            infos.add(s);
        }

        /**
         * Prints out all the info messages to the given logger.
         */
        public void printInfos(PrintStream logger) {
            infos.forEach(logger::println);
        }
    }
}
