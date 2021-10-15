package io.jenkins.plugins.pipeline.cache;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;

import org.junit.Test;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class ConfigurationTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Test
    public void testConfiguration() {
        rr.then(r -> {
            assertNull("should be empty initially", Configuration.get().getUsername());
            assertNull("should be empty initially", Configuration.get().getPassword());
            assertNull("should be empty initially", Configuration.get().getBucket());
            assertNull("should be empty initially", Configuration.get().getRegion());
            assertNull("should be empty initially", Configuration.get().getEndpoint());
            assertEquals(0, Configuration.get().getSizeThresholdMb());

            setTextInput(r,"username", "alice");
            setTextInput(r,"password", "secret");
            setTextInput(r,"bucket", "blue");
            setTextInput(r,"region", "dc1");
            setTextInput(r,"endpoint", "http://localhost:9000");
            setLongInput(r,"sizeThresholdMb", 777);

            assertEquals("should be editable", "alice", Configuration.get().getUsername());
            assertEquals("should be editable", "secret", Configuration.get().getPassword());
            assertEquals("should be editable", "blue", Configuration.get().getBucket());
            assertEquals("should be editable", "dc1", Configuration.get().getRegion());
            assertEquals("should be editable", "http://localhost:9000", Configuration.get().getEndpoint());
            assertEquals("should be editable", 777, Configuration.get().getSizeThresholdMb());
        });
        rr.then(r -> {
            assertEquals("should be still there after restart of Jenkins", "alice", Configuration.get().getUsername());
            assertEquals("should be still there after restart of Jenkins", "secret", Configuration.get().getPassword());
            assertEquals("should be still there after restart of Jenkins", "blue", Configuration.get().getBucket());
            assertEquals("should be still there after restart of Jenkins", "dc1", Configuration.get().getRegion());
            assertEquals("should be still there after restart of Jenkins", "http://localhost:9000", Configuration.get().getEndpoint());
            assertEquals("should be still there after restart of Jenkins", 777, Configuration.get().getSizeThresholdMb());
        });
    }

    private void setTextInput(JenkinsRule r, String fieldName, String value) throws Exception {
        HtmlForm config = r.createWebClient().goTo("configure").getFormByName("config");
        HtmlInput input = config.getInputByName("_." + fieldName);
        input.setValueAttribute(value);
        r.submit(config);
    }

    private void setLongInput(JenkinsRule r, String fieldName, long value) throws Exception {
        HtmlForm config = r.createWebClient().goTo("configure").getFormByName("config");
        HtmlInput input = config.getInputByName("_." + fieldName);
        input.setValueAttribute(Long.toString(value));
        r.submit(config);
    }

}
