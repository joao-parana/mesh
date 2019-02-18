package com.gentics.mesh.search.verticle.eventhandler;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.core.data.search.IndexHandler;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.search.IndexHandlerRegistry;
import com.gentics.mesh.search.SearchProvider;
import com.gentics.mesh.search.index.metric.SyncMetric;
import com.gentics.mesh.search.verticle.MessageEvent;
import com.gentics.mesh.search.verticle.request.ElasticsearchRequest;
import dagger.Lazy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.gentics.mesh.core.rest.MeshEvent.INDEX_SYNC_WORKER_ADDRESS;

/**
 * Verticle which will execute the elasticsearch sync.
 */
public class SyncHandler implements EventHandler {

	private static final Logger log = LoggerFactory.getLogger(SyncHandler.class);

	private Lazy<IndexHandlerRegistry> registry;

	private SearchProvider provider;

	/**
	 * Send the index sync event which will trigger the index sync job.
	 */
	public static void invokeSync() {
		Mesh.mesh().getVertx().eventBus().send(INDEX_SYNC_WORKER_ADDRESS.address, null);
	}

	@Inject
	public SyncHandler(Lazy<IndexHandlerRegistry> registry, SearchProvider provider) {
		this.registry = registry;
		this.provider = provider;
	}

	private Completable syncIndices() {
		return Observable.fromIterable(registry.get().getHandlers())
			.flatMapCompletable(handler -> handler.init().andThen(handler.syncIndices()));
	}

	private Completable purgeOldIndices() {
		List<IndexHandler<?>> handlers = registry.get().getHandlers();
		Single<Set<String>> allIndices = provider.listIndices();
		Observable<IndexHandler<?>> obs = Observable.fromIterable(handlers);

		return allIndices.flatMapCompletable(indices -> {
			return obs.flatMapCompletable(handler -> {
				Set<String> unknownIndices = handler.filterUnknownIndices(indices);
				return Observable.fromIterable(unknownIndices).flatMapCompletable(index -> {
					log.info("Deleting unknown index {" + index + "}");
					return provider.deleteIndex(index);
				});
			});
		});
	}

	@Override
	public Flowable<ElasticsearchRequest> handle(MessageEvent messageEvent) {
		return Completable.fromAction(() -> {
			log.info("Processing index sync job.");
			SyncMetric.reset();
		})
			.andThen(purgeOldIndices())
			.andThen(syncIndices())
			.andThen(provider.refreshIndex()).doOnComplete(() -> {
				log.info("Sync completed");
				vertx.eventBus().publish(MeshEvent.INDEX_SYNC.address, new JsonObject().put("status", "completed"));
			}).doOnError(error -> {
				log.error("Sync failed", error);
				vertx.eventBus().publish(MeshEvent.INDEX_SYNC.address, new JsonObject().put("status", "failed"));
			});
	}

	@Override
	public Collection<MeshEvent> handledEvents() {
		return Collections.singletonList(MeshEvent.INDEX_SYNC_WORKER_ADDRESS);
	}
}
