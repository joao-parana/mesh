package com.gentics.mesh.test.context;

import java.net.ServerSocket;
import java.util.function.Consumer;

import com.gentics.mesh.etc.config.AuthenticationOptions;
import com.gentics.mesh.etc.config.MeshOptions;
import com.gentics.mesh.etc.config.OAuth2Options;

public enum MeshOptionChanger {
	NO_CHANGE(ignore -> {
	}), SMALL_EVENT_BUFFER(options -> {
		options.getSearchOptions().setEventBufferSize(100);
	}), NO_PATH_CACHE(options -> {
		options.getCacheConfig().setPathCacheSize(0);
	}), NO_UPLOAD_PARSER(options -> {
		options.getUploadOptions().setParser(false);
	}), EXCLUDE_BINARY_SEARCH(options -> {
		options.getSearchOptions().setIncludeBinaryFields(false);
	}), RANDOM_ES_PORT(options -> {
		try {
			try (ServerSocket s = new ServerSocket(0)) {
				options.getSearchOptions().setTimeout(500L);
				options.getSearchOptions().setUrl("http://localhost:" + s.getLocalPort());
			}
		} catch (Exception e) {
			throw new RuntimeException("Could not find free port", e);
		}
	});

	final Consumer<MeshOptions> changer;

	MeshOptionChanger(Consumer<MeshOptions> changer) {
		this.changer = changer;
	}
}
