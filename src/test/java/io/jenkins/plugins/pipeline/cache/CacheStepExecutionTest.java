package io.jenkins.plugins.pipeline.cache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import hudson.EnvVars;

@RunWith(MockitoJUnitRunner.class)
public class CacheStepExecutionTest {

    @Mock
    private StepContext stepContext;

    @Mock
    private Cache cache;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    @Before
    public void setupWorkspace() throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();
        envVars.put("WORKSPACE", workspace.getRoot().getAbsolutePath());
        Files.write(Paths.get(workspace.getRoot().getAbsolutePath(), "pom.xml"), "content".getBytes(StandardCharsets.UTF_8));
        when(stepContext.get(EnvVars.class)).thenReturn(envVars);
    }

    @Test
    public void testWithoutHash() throws IOException, InterruptedException {
        String key = UUID.randomUUID().toString();
        CacheStep step = new CacheStep(folder.getRoot().getAbsolutePath(), key);

        String[] result = new CacheStepExecution(stepContext, step, cache).createRestoreKeys();

        assertThat(result.length, is(1));
        assertThat(result[0], is(key));
    }

    @Test
    public void testWithHash() throws IOException, InterruptedException {
        String key = UUID.randomUUID().toString();
        CacheStep step = new CacheStep(folder.getRoot().getAbsolutePath(), key);
        step.setHashFiles("**/pom.xml");

        String[] result = new CacheStepExecution(stepContext, step, cache).createRestoreKeys();

        assertThat(result.length, is(2));
        assertThat(result[0], is(key+"-9a0364b9e99bb480dd25e1f0284c8555"));
        assertThat(result[1], is(key));
    }

}