package io.jenkins.plugins.pipeline.cache;

import com.gargoylesoftware.htmlunit.html.HtmlForm;

import org.junit.Test;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class ConfigurationTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Test
    public void testConfiguration() {
        // WHEN
        rr.then(r -> {
            // THEN
            assertNull("should be empty initially", CacheConfiguration.get().getUsername());
            assertNull("should be empty initially", CacheConfiguration.get().getPassword());
            assertNull("should be empty initially", CacheConfiguration.get().getBucket());
            assertNull("should be empty initially", CacheConfiguration.get().getRegion());
            assertNull("should be empty initially", CacheConfiguration.get().getEndpoint());
            assertEquals(0, CacheConfiguration.get().getThreshold());

            // WHEN
            HtmlForm config = r.createWebClient().goTo("configure").getFormByName("config");
            config.getInputByName("_.username").setValueAttribute("alice");
            config.getInputByName("_.password").setValueAttribute("secret");
            config.getInputByName("_.bucket").setValueAttribute("blue");
            config.getInputByName("_.region").setValueAttribute("dc1");
            config.getInputByName("_.endpoint").setValueAttribute("http://localhost:9000");
            config.getInputByName("_.threshold").setValueAttribute(Long.toString(777));
            r.submit(config);

            // THEN
            assertEquals("should be editable", "alice", CacheConfiguration.get().getUsername());
            assertEquals("should be editable", "secret", CacheConfiguration.get().getPassword().getPlainText());
            assertEquals("should be editable", "blue", CacheConfiguration.get().getBucket());
            assertEquals("should be editable", "dc1", CacheConfiguration.get().getRegion());
            assertEquals("should be editable", "http://localhost:9000", CacheConfiguration.get().getEndpoint());
            assertEquals("should be editable", 777, CacheConfiguration.get().getThreshold());
        });
        // WHEN
        rr.then(r -> {
            // THEN
            assertEquals("should be still there after restart of Jenkins", "alice", CacheConfiguration.get().getUsername());
            assertEquals("should be still there after restart of Jenkins", "secret", CacheConfiguration.get().getPassword().getPlainText());
            assertEquals("should be still there after restart of Jenkins", "blue", CacheConfiguration.get().getBucket());
            assertEquals("should be still there after restart of Jenkins", "dc1", CacheConfiguration.get().getRegion());
            assertEquals("should be still there after restart of Jenkins", "http://localhost:9000", CacheConfiguration.get().getEndpoint());
            assertEquals("should be still there after restart of Jenkins", 777, CacheConfiguration.get().getThreshold());
        });
    }

}
