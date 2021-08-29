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
            assertNull("not set initially", Configuration.get().getUsername());
            assertNull("not set initially", Configuration.get().getPassword());
            assertNull("not set initially", Configuration.get().getBucket());
            assertNull("not set initially", Configuration.get().getRegion());
            assertNull("not set initially", Configuration.get().getEndpoint());

            setTextInput(r,"username", "alice");
            setTextInput(r,"password", "secret");
            setTextInput(r,"bucket", "blue");
            setTextInput(r,"region", "dc1");
            setTextInput(r,"endpoint", "http://localhost:9000");

            assertEquals("global config page let us edit it", "alice", Configuration.get().getUsername());
            assertEquals("global config page let us edit it", "secret", Configuration.get().getPassword());
            assertEquals("global config page let us edit it", "blue", Configuration.get().getBucket());
            assertEquals("global config page let us edit it", "dc1", Configuration.get().getRegion());
            assertEquals("global config page let us edit it", "http://localhost:9000", Configuration.get().getEndpoint());
        });
        rr.then(r -> {
            assertEquals("still there after restart of Jenkins", "alice", Configuration.get().getUsername());
            assertEquals("still there after restart of Jenkins", "secret", Configuration.get().getPassword());
            assertEquals("still there after restart of Jenkins", "blue", Configuration.get().getBucket());
            assertEquals("still there after restart of Jenkins", "dc1", Configuration.get().getRegion());
            assertEquals("still there after restart of Jenkins", "http://localhost:9000", Configuration.get().getEndpoint());
        });
    }

    private void setTextInput(JenkinsRule r, String fieldName, String value) throws Exception {
        HtmlForm config = r.createWebClient().goTo("configure").getFormByName("config");
        HtmlInput textbox = config.getInputByName("_." + fieldName);
        textbox.setValueAttribute(value);
        r.submit(config);
    }

}
