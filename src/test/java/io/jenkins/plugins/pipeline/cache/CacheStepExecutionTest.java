package io.jenkins.plugins.pipeline.cache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import hudson.EnvVars;

/**
 * Checks that the file hash is working as expected.
 */
@RunWith(MockitoJUnitRunner.class)
public class CacheStepExecutionTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();
    @Mock
    private StepContext stepContext;
    @Mock
    private Cache cache;

    @Test
    public void testOneFile() throws IOException, InterruptedException {
        // GIVEN
        setupWorkspace();
        createWorkspaceFile("content", "pom.xml");

        // GIVEN
        String key = UUID.randomUUID().toString();
        CacheStep step = new CacheStep(folder.getRoot().getAbsolutePath(), key, "**/pom.xml");

        // WHEN
        String hash = new CacheStepExecution(stepContext, step, cache).createFileHash();

        // THEN
        assertThat(hash, is("9a0364b9e99bb480dd25e1f0284c8555"));
    }

    @Test
    public void testMultipleFiles() throws IOException, InterruptedException {
        // GIVEN
        setupWorkspace();
        createWorkspaceFile("content", "pom.xml");
        createWorkspaceFile("content", "a", "pom.xml");
        createWorkspaceFile("content", "b", "pom.xml");

        // GIVEN
        String key = UUID.randomUUID().toString();
        CacheStep step = new CacheStep(folder.getRoot().getAbsolutePath(), key, "**/pom.xml");

        // WHEN
        String hash = new CacheStepExecution(stepContext, step, cache).createFileHash();

        // THEN
        assertThat(hash, is("5ca9b19b4f762286ff5c631d3703b71a"));
    }

    @Test
    public void testMultipleFilesDifferentOrdered() throws IOException, InterruptedException {
        // GIVEN
        setupWorkspace();
        createWorkspaceFile("content", "b", "pom.xml");
        createWorkspaceFile("content", "a", "pom.xml");
        createWorkspaceFile("content", "pom.xml");

        // GIVEN
        String key = UUID.randomUUID().toString();
        CacheStep step = new CacheStep(folder.getRoot().getAbsolutePath(), key, "**/pom.xml");

        // WHEN
        String hash = new CacheStepExecution(stepContext, step, cache).createFileHash();

        // THEN
        assertThat(hash, is("5ca9b19b4f762286ff5c631d3703b71a"));
    }

    private void setupWorkspace() throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();
        envVars.put("WORKSPACE", workspace.getRoot().getAbsolutePath());
        when(stepContext.get(EnvVars.class)).thenReturn(envVars);
    }

    private void createWorkspaceFile(String content, String ... path) throws IOException {
        Path file = Paths.get(workspace.getRoot().getAbsolutePath(), path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

}