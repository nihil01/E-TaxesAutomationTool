package com.bizcon.taxesautomator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public record CustomHttpHandler<T>(Class<T> responseType) implements HttpResponse.BodyHandler<T> {

    private static final ObjectMapper mapper = new ObjectMapper();


    @Override
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {

        if (responseInfo.statusCode() >= 400) {
            return HttpResponse.BodySubscribers.replacing(null);
        }

        HttpResponse.BodySubscriber<String> stringSubscriber =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                        .apply(responseInfo);

        Function<String, T> mapperFn = s -> {
            try {
                return mapper.readValue(s, responseType);
            } catch (JsonProcessingException e) {
                return null;
            }
        };

        return HttpResponse.BodySubscribers.mapping(stringSubscriber, mapperFn);

    }
}
