package io.jenkins.plugins.pipeline.cache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import hudson.FilePath;

public class CacheTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @Rule
    public TemporaryFolder folderA = new TemporaryFolder();

    @Rule
    public TemporaryFolder folderB = new TemporaryFolder();

    private String bucket;

    private Cache cache;

    @Before
    public void setup() throws IOException, InterruptedException {
        bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);
        cache = new Cache(minio.accessKey(), minio.secretKey(), bucket, "us-west-1", minio.getExternalAddress());
    }

    @Test
    public void testBackupAndRestore() throws IOException, InterruptedException {
        // GIVEN
        String key = UUID.randomUUID().toString();
        folderA.newFile("a");
        folderA.newFolder("b");
        folderA.newFile("b" + File.separator + "c");
        folderA.newFolder("d");

        // WHEN
        cache.backup(new FilePath(folderA.getRoot()), key);
        cache.restore(new FilePath(folderB.getRoot()), key);

        // THEN
        assertThat(new File(folderB.getRoot(), "a").isFile(), is(true));
        assertThat(new File(folderB.getRoot(), "b").isDirectory(), is(true));
        assertThat(new File(folderB.getRoot(), "b" + File.separator + "c").isFile(), is(true));
        assertThat(new File(folderB.getRoot(), "d").isDirectory(), is(false));
    }

    @Test
    public void testBackupAndRestoreWithLargerFiles() throws IOException, InterruptedException {
        // GIVEN
        String key = UUID.randomUUID().toString();
        File fileA = new File(folderA.getRoot(), "a");
        File fileB = new File(folderA.getRoot(), "b");
        File fileC = new File(folderA.getRoot(), "c");
        appendRandomContent(fileA, 10);
        appendRandomContent(fileB, 20);
        appendRandomContent(fileC, 100);

        // WHEN
        cache.backup(new FilePath(folderA.getRoot()), key);
        cache.restore(new FilePath(folderB.getRoot()), key);

        // THEN
        assertThat(fileA.length(), is(new File(folderB.getRoot(), "a").length()));
        assertThat(fileB.length(), is(new File(folderB.getRoot(), "b").length()));
        assertThat(fileC.length(), is(new File(folderB.getRoot(), "c").length()));
    }

    @Test
    public void testBackupAndRestoreWithLongFileNames() throws IOException, InterruptedException {
        // GIVEN
        String key = UUID.randomUUID().toString();
        File file = folderA.newFile(RandomStringUtils.randomAlphanumeric(101));

        // WHEN
        cache.backup(new FilePath(folderA.getRoot()), key);
        cache.restore(new FilePath(folderB.getRoot()), key);

        // THEN
        assertThat(new File(folderB.getRoot(), file.getName()).exists(), is(true));
    }

    @Test
    public void testRestoreNewCache() throws IOException, InterruptedException {
        cache.restore(new FilePath(folderB.getRoot()), UUID.randomUUID().toString());

        assertThat(folderB.getRoot().list().length, is(0));
    }

    @Test
    public void testRestoreNewFolder() throws IOException, InterruptedException {
        // GIVEN
        String key = UUID.randomUUID().toString();
        File file = folderA.newFile(RandomStringUtils.randomAlphanumeric(101));

        // WHEN
        cache.backup(new FilePath(folderA.getRoot()), key);
        cache.restore(new FilePath(new File(folderB.getRoot(), "temp")), key);

        // THEN
        assertThat(Paths.get(folderB.getRoot().getAbsolutePath(), "temp", file.getName()).toFile().exists(), is(true));
    }

    @Test
    public void testFindByExactMatch() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, "prefix-1");
        mc.createObject(bucket, "prefix-2");
        mc.createObject(bucket, "prefix-3");
        mc.createObject(bucket, "prefix-4");
        mc.createObject(bucket, "prefix-5");

        // WHEN
        String key = cache.findKey("prefix-3");

        // THEN
        assertThat(key, is("prefix-3"));
    }

    @Test
    public void testFindByPrefix() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, "prefax-1");
        mc.createObject(bucket, "prefox-2");
        mc.createObject(bucket, "prefix-5");
        mc.createObject(bucket, "prefux-4");
        mc.createObject(bucket, "prefex-3");

        // WHEN
        String key = cache.findKey("prefix");

        // THEN
        assertThat(key, is("prefix-5"));
    }

    @Test
    public void testFindByPrefixLatestOne() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, "prefix-1");
        mc.createObject(bucket, "prefix-2");
        mc.createObject(bucket, "prefix-5");
        mc.createObject(bucket, "prefix-4");
        mc.createObject(bucket, "prefix-3");

        // WHEN
        String key = cache.findKey("prefix");

        // THEN
        assertThat(key, is("prefix-3"));
    }

    @Test
    public void testFindWithoutMatches() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, "prefax-1");
        mc.createObject(bucket, "prefex-2");
        mc.createObject(bucket, "prefix-5");
        mc.createObject(bucket, "prefox-4");

        // WHEN
        String key = cache.findKey("prefux");

        // THEN
        assertThat(key, nullValue());
    }

    @Test
    public void testFindByOrder() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, "prefix-1");
        mc.createObject(bucket, "prefix-2");
        mc.createObject(bucket, "prefix-5");
        mc.createObject(bucket, "prefix-4");
        mc.createObject(bucket, "prefix-3");

        // WHEN
        String key = cache.findKey("pref", "prefix-5");

        // THEN
        assertThat(key, is("prefix-3"));
    }

    @Test
    public void testFindKeyNull() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, UUID.randomUUID().toString());

        // WHEN
        String key = cache.findKey((String[]) null);

        // THEN
        assertThat(key, nullValue());
    }

    @Test
    public void testFindKeyEmpty() throws IOException, InterruptedException {
        // GIVEN
        mc.createObject(bucket, UUID.randomUUID().toString());

        // WHEN
        String key = cache.findKey("");

        // THEN
        assertThat(key, nullValue());
    }

    private static void appendRandomContent(File file, int sizeInMB) throws IOException {
        Random rand = new Random();
        byte[] bytes = new byte[1024 * 1024];
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            for (int i = 0; i < sizeInMB; i++) {
                rand.nextBytes(bytes);
                bos.write(bytes);
            }
        }
    }
}