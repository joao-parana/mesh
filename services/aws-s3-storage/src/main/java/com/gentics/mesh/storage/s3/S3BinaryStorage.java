package com.gentics.mesh.storage.s3;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.reactivestreams.Publisher;

import com.gentics.mesh.core.data.node.field.BinaryGraphField;
import com.gentics.mesh.storage.AbstractBinaryStorage;
import com.gentics.mesh.util.RxUtil;

import hu.akarnokd.rxjava2.interop.CompletableInterop;
import hu.akarnokd.rxjava2.interop.FlowableInterop;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.file.AsyncFile;
import io.vertx.reactivex.core.file.FileProps;
import io.vertx.reactivex.core.file.FileSystem;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Singleton
public class S3BinaryStorage extends AbstractBinaryStorage {

	private static final Logger log = LoggerFactory.getLogger(S3BinaryStorage.class);

	private S3AsyncClient client;

	private S3StorageOptions options;

	private final Vertx rxVertx;

	private FileSystem fs;

	@Inject
	public S3BinaryStorage(S3StorageOptions options, Vertx rxVertx) {
		this.options = options;
		this.rxVertx = rxVertx;
		this.fs = rxVertx.fileSystem();
		init();
	}

	private void init() {
		AwsCredentials credentials = AwsBasicCredentials.create(options.getAccessId(), options.getAccessKey());
		// ClientConfiguration clientConfiguration = new ClientConfiguration();
		// clientConfiguration.setSignerOverride("AWSS3V4SignerType");

		// ClientAsyncHttpConfiguration asyncHttpConfiguration = ClientAsyncHttpConfiguration.builder().build();
		// S3AdvancedConfiguration advancedConfiguration = S3AdvancedConfiguration.builder().build();
		// DefaultCredentialsProvider.create();

		client = S3AsyncClient.builder()
			// .advancedConfiguration(advancedConfiguration)
			// .asyncHttpConfiguration(asyncHttpConfiguration)
			.region(Region.of(options.getRegion()))
			.endpointOverride(URI.create(options.getUrl()))
			.credentialsProvider(StaticCredentialsProvider.create(credentials))
			.build();

		String bucketName = options.getBucketName();

		// try {
		// HeadBucketResponse response = client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).get();
		// } catch (InterruptedException | ExecutionException e) {
		// TODO Auto-generated catch block
		// e.printStackTrace();
		// } catch (NoSuchKeyException e) {
		try {
			CreateBucketResponse response2 = client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).get();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// }

		//
		// String bucketName = options.getBucketName();
		// if (!s3Client.doesBucketExist(bucketName)) {
		// log.info("Did not find bucket {" + bucketName + "}. Creating it...");
		// s3Client.createBucket(new CreateBucketRequest(bucketName));
		// }

	}

	@Override
	public boolean exists(BinaryGraphField field) {
		String id = field.getBinary().getSHA512Sum();
		// NoSuchKeyException
		try {
			HeadObjectRequest request = HeadObjectRequest.builder()
				.bucket(options.getBucketName())
				.key(id)
				.build();
			client.headObject(request).get();
			return true;
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (NoSuchElementException e) {
			return false;
		}

	}

	@Override
	public Flowable<Buffer> read(String uuid) {
		return Flowable.defer(() -> {
			if (log.isDebugEnabled()) {
				log.debug("Loading data for hash {" + uuid + "}");
			}
			// GetObjectRequest rangeObjectRequest = new GetObjectRequest(options.getBucketName(), hashsum);
			GetObjectRequest request = GetObjectRequest.builder()
				.bucket(options.getBucketName())
				.key(uuid)
				.build();
			return FlowableInterop.fromFuture(client.getObject(request, AsyncResponseTransformer.toBytes()));
		}).map(f -> Buffer.buffer(f.asByteArray()));
	}

	@Override
	public Completable storeInTemp(String sourceFilePath, String temporaryId) {
		Single<AsyncFile> open = fs.rxOpen(sourceFilePath, new OpenOptions());
		Single<FileProps> props = fs.rxProps(sourceFilePath);
		return Single.zip(open, props, (file, info) -> {
			Flowable<Buffer> stream = RxUtil.toBufferFlow(file);
			long size = info.size();
			return storeInTemp(stream, size, temporaryId);
		}).doOnError(e -> {
			log.error("Error while storing file {} in temp with id {}", sourceFilePath, temporaryId, e);
		}).toCompletable();
	}

	@Override
	public Completable storeInTemp(Flowable<Buffer> stream, long size, String temporaryId) {
		return Completable.defer(() -> {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(options.getBucketName())
				.key(temporaryId)
				.contentLength(size)
				.build();

			Publisher<ByteBuffer> publisher = stream.map(b -> ByteBuffer.wrap(b.getBytes()));
			return CompletableInterop.fromFuture(client.putObject(request, AsyncRequestBody.fromPublisher(publisher)));
		});
	}

	@Override
	public Completable delete(String uuid) {
		return Completable.complete();
	}

	@Override
	public Buffer readAllSync(String uuid) {
		// TODO implement
		return null;
	}

	@Override
	public Completable moveInPlace(String uuid, String temporaryId) {
		return Completable.complete();
	}

	@Override
	public Completable purgeTemporaryUpload(String temporaryId) {
		return Completable.complete();
	}
}
