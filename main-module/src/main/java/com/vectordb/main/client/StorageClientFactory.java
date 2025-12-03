package com.vectordb.main.client;

import com.vectordb.common.serialization.SearchResultDeserializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class StorageClientFactory {

    private final WebClient.Builder storageWebClientBuilder;
    private final SearchResultDeserializer searchResultDeserializer;

    public StorageClient create(URI baseUri) {
        WebClient client = storageWebClientBuilder.clone()
                .baseUrl(baseUri.toString())
                .build();
        return new StorageClient(client, searchResultDeserializer);
    }
}


