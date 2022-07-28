package io.jenkins.plugins.pipeline.cache.s3;

import static io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository.CREATION;
import static io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository.LAST_ACCESS;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;

/**
 * {@link OutputStream} which allows writing an object to S3 directly. If the content size is not greater than 10 MB then it is uploaded at
 * once, otherwise in chunks (default: 10 MB).
 */
public class S3OutputStream extends OutputStream {

    /**
     * Buffer size (default: 10 MB, minimum: 5 MB).
     */
    public static final int BUFFER_SIZE = 1024 * 1024 * 10;

    /**
     * S3 client which is used to upload the content.
     */
    private final AmazonS3 s3;

    /**
     * Name of the bucket where the object is stored.
     */
    private final String bucket;

    /**
     * Key which is assigned to the object within the bucket.
     */
    private final String key;

    /**
     * The internal buffer where data is stored.
     */
    protected byte[] buf;

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range <tt>0</tt> through <tt>buf.length</tt>; elements
     * <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid
     * byte data.
     */
    protected int count;

    /**
     * Holds the part IDs in case of a multipart upload.
     */
    protected List<PartETag> partIDs = new ArrayList<>();

    /**
     * true indicates that the stream is still open, otherwise false.
     */
    protected boolean open = true;

    /**
     * Holds the result of the initial upload in case of a multipart upload.
     */
    private InitiateMultipartUploadResult multipartUpload;

    /**
     * Creates a new buffered output stream to write data to S3.
     * @param s3 the AmazonS3 client
     * @param bucket name of the bucket
     * @param key key of the object within the bucket
     */
    public S3OutputStream(AmazonS3 s3, String bucket, String key) {
        this(s3, bucket, key, BUFFER_SIZE);
    }

    /**
     * Creates a new buffered output stream to write data to S3.
     * @param s3 the AmazonS3 client
     * @param bucket name of the bucket
     * @param key key of the object within the bucket
     * @param size size of the buffer
     */
    public S3OutputStream(AmazonS3 s3, String bucket, String key, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
        this.buf = new byte[size];
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param b the byte to be written.
     */
    public synchronized void write(int b) {
        if (count >= buf.length) {
            flushAndReset();
        }
        buf[count++] = (byte)b;
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this buffered output stream.
     * @param b the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     */
    public synchronized void write(byte[] b, int off, int len) {
        while (count + len > buf.length) {
            int size = buf.length - count;
            System.arraycopy(b, off, buf, count, size);
            off += size;
            len -= size;
            count += size;
            flushAndReset();
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    private void flushAndReset() {
        if (count <= 0) {
            return;
        }

        if (multipartUpload == null) {
            // initialize partial upload
            multipartUpload = s3.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key)
                    .withObjectMetadata(createMetadata(true)));
        }

        // upload part
        UploadPartResult uploadResult = s3.uploadPart(new UploadPartRequest()
                .withBucketName(bucket)
                .withKey(key)
                .withUploadId(multipartUpload.getUploadId())
                .withInputStream(new ByteArrayInputStream(buf, 0, count))
                .withPartNumber(partIDs.size() + 1)
                .withPartSize(count));

        // store part ID (required for the final step)
        partIDs.add(uploadResult.getPartETag());

        // reset count
        count = 0;
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;

        // complete partial upload
        if (multipartUpload != null) {
            flushAndReset();
            s3.completeMultipartUpload(new CompleteMultipartUploadRequest(bucket, key, multipartUpload.getUploadId(), partIDs));
        }

        // or upload content at once (content <= buffer size)
        else {
            s3.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(buf, 0, count), createMetadata(false)));
        }
    }

    private ObjectMetadata createMetadata(boolean multipart) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata(CREATION, Long.toString(System.currentTimeMillis()));
        metadata.addUserMetadata(LAST_ACCESS, Long.toString(System.currentTimeMillis()));

        if (!multipart) {
            metadata.setContentLength(count);
        }

        return metadata;
    }

}
