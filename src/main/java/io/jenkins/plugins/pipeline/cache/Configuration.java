package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.io.Serializable;

import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

/**
 * Pipeline cache configuration.
 */
@Extension
@Symbol("pipeline-cache")
public class Configuration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String bucket;
    private String region;
    private String endpoint;
    private long sizeThresholdMb; // stored in MB

    public Configuration() {
        load();
    }

    public static Configuration get() {
        return ExtensionList.lookupSingleton(Configuration.class);
    }

    public String getUsername() {
        return username;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
        save();
    }

    public String getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setPassword(String password) {
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

    public long getSizeThresholdMb() {
        return sizeThresholdMb;
    }

    /**
     * @param sizeThresholdMb Storage size threshold in megabytes (1e6 bytes)
     */
    @DataBoundSetter
    public void setSizeThresholdMb(long sizeThresholdMb) {
        this.sizeThresholdMb = sizeThresholdMb;
        save();
    }

    public FormValidation doCheckSizeThresholdMb(@QueryParameter String value) throws IOException, ServletException {
        try {
            Integer.parseInt(value);
            return FormValidation.ok();
        } catch (NumberFormatException e) {
            return FormValidation.error("Not an integer");
        }
    }
}
