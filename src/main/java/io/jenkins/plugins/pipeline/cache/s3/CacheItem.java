package io.jenkins.plugins.pipeline.cache.s3;

/**
 * Represents one cache item.
 */
public class CacheItem {
    private final String key;
    private final long contentLength;
    private final long lastAccess;

    /**
     * @param key Unique identifier (e.g. maven-d41d8cd98f00b204e9800998ecf8427e)
     * @param contentLength Size of the cache in byte
     * @param lastAccess Unix time in ms when the item was accessed last
     */
    public CacheItem(String key, long contentLength, long lastAccess) {
        this.key = key;
        this.contentLength = contentLength;
        this.lastAccess = lastAccess;
    }

    public String getKey() {
        return key;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getLastAccess() {
        return lastAccess;
    }
}
