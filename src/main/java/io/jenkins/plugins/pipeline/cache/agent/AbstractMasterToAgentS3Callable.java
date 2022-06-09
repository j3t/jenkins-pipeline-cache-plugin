package io.jenkins.plugins.pipeline.cache.agent;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.jenkins.plugins.pipeline.cache.CacheConfiguration;
import io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository;
import jenkins.MasterToSlaveFileCallable;

/**
 * Base class for S3 related operations. Note: Plugin code is executed on the master node but the files we want to cache are located on the
 * build agent. In order to process the files on the agent, we use {@link MasterToSlaveFileCallable}.
 */
public abstract class AbstractMasterToAgentS3Callable extends MasterToSlaveFileCallable<AbstractMasterToAgentS3Callable.Result> {

    protected final CacheConfiguration config;
    private volatile CacheItemRepository cacheItemRepository;

    protected AbstractMasterToAgentS3Callable(CacheConfiguration config) {
        this.config = config;
    }

    protected CacheItemRepository cacheItemRepository() {
        if (cacheItemRepository == null) {
            synchronized (this) {
                cacheItemRepository = new CacheItemRepository(
                        config.getUsername(),
                        config.getPassword().getPlainText(),
                        config.getRegion(),
                        config.getEndpoint(),
                        config.getBucket()
                );
            }
        }

        return cacheItemRepository;
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

    protected String performanceString(String key, long startNanoTime) {
        double duration = (System.nanoTime() - startNanoTime) / 1000000000D;
        long size = cacheItemRepository().getContentLength(key);
        long speed = (long) (size / duration);
        return String.format("%s bytes in %.2f secs (%s bytes/sec)", size, duration, speed);
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
