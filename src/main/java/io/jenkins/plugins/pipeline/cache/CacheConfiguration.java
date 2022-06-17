package io.jenkins.plugins.pipeline.cache;

import java.io.Serializable;
import java.util.Objects;

import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.Secret;
import io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Global cache configuration.
 */
@Extension
@Symbol("pipeline-cache")
public class CacheConfiguration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private Secret password;
    private String bucket;
    private String region;
    private String endpoint;
    private long threshold;

    public CacheConfiguration() {
        load();
    }

    public static CacheConfiguration get() {
        return ExtensionList.lookupSingleton(CacheConfiguration.class);
    }

    public String getUsername() {
        return username;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
        save();
    }

    public Secret getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
        save();
    }

    public String getBucket() {
        return bucket;
    }

    @DataBoundSetter
    public void setBucket(String bucket) {
        this.bucket = bucket;
        save();
    }

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
        save();
    }

    public String getEndpoint() {
        return endpoint;
    }

    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        save();
    }

    public long getThreshold() {
        return threshold;
    }

    /**
     * @param threshold threshold in megabyte when the system removes last recently used item from the cache
     */
    @DataBoundSetter
    public void setThreshold(long threshold) {
        this.threshold = threshold;
        save();
    }

    public FormValidation doCheckThreshold(@QueryParameter String value) {
        try {
            Integer.parseInt(value);
            return FormValidation.ok();
        } catch (NumberFormatException e) {
            return FormValidation.error("Not an integer");
        }
    }

    @POST
    public FormValidation doTestConnection(
            @QueryParameter String username,
            @QueryParameter String password,
            @QueryParameter String bucket,
            @QueryParameter String region,
            @QueryParameter String endpoint) {
        Objects.requireNonNull(Jenkins.get()).checkPermission(Jenkins.ADMINISTER);

        try {
            CacheItemRepository repo = new CacheItemRepository(username, password, region, endpoint, bucket);

            if (repo.bucketExists()) {
                return FormValidation.ok("OK");
            }
            return FormValidation.error("Bucket not exists");
        } catch (Exception e) {
            return FormValidation.error(e.getMessage());
        }
    }

}
