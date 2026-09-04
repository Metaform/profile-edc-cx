/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.dataspacetck.cx.usecase;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Calls an application API through the data plane an {@link Edr} points at.
 * <p>
 * Deliberately a plain static helper rather than an injected service: unlike the DCP token provider, which
 * needs the launcher's key material and per-scope credential state, calling an application API is a pure
 * function of the EDR, a path and a body. There is nothing to configure and nothing to bind to a test
 * scope, so routing it through dependency injection would add indirection without adding capability.
 */
public final class DataPlaneRequests {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DataPlaneRequests() {
    }

    /**
     * POSTs a body, serialized as JSON, to a path relative to the EDR endpoint.
     *
     * @param edr  the EDR to call through
     * @param path the path relative to the EDR endpoint, with or without a leading slash
     * @param body the request body; a String is sent verbatim, anything else is serialized with Jackson
     * @return the response
     */
    public static DataPlaneResponse postJson(Edr edr, String path, Object body) {
        return postJson(edr, path, body, Map.of());
    }

    /**
     * POSTs a body, serialized as JSON, adding the given headers.
     *
     * @param edr     the EDR to call through
     * @param path    the path relative to the EDR endpoint, with or without a leading slash
     * @param body    the request body; a String is sent verbatim, anything else is serialized with Jackson
     * @param headers additional request headers
     * @return the response
     */
    public static DataPlaneResponse postJson(Edr edr, String path, Object body, Map<String, String> headers) {
        // a String body is passed through unserialized so a test can send deliberately malformed JSON
        var json = body instanceof String raw ? raw : MAPPER.writeValueAsString(body);
        return execute(requestBuilder(edr, path, headers).post(RequestBody.create(json, JSON)).build());
    }

    /**
     * GETs a path relative to the EDR endpoint.
     *
     * @param edr  the EDR to call through
     * @param path the path relative to the EDR endpoint, with or without a leading slash
     * @return the response
     */
    public static DataPlaneResponse get(Edr edr, String path) {
        return get(edr, path, Map.of());
    }

    /**
     * GETs a path relative to the EDR endpoint, adding the given headers.
     *
     * @param edr     the EDR to call through
     * @param path    the path relative to the EDR endpoint, with or without a leading slash
     * @param headers additional request headers
     * @return the response
     */
    public static DataPlaneResponse get(Edr edr, String path, Map<String, String> headers) {
        return execute(requestBuilder(edr, path, headers).get().build());
    }

    /**
     * Joins an EDR endpoint with a relative path, tolerating a trailing slash on the endpoint and a
     * missing or present leading slash on the path.
     *
     * @param endpoint the EDR endpoint
     * @param path     the relative path, possibly empty
     * @return the absolute URL
     */
    public static String resolveUrl(String endpoint, String path) {
        var base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (path == null || path.isEmpty()) {
            return base;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static Request.Builder requestBuilder(Edr edr, String path, Map<String, String> headers) {
        if (edr.endpoint() == null || edr.endpoint().isBlank()) {
            throw new AssertionError("The transfer produced no data-plane endpoint, so the application API " +
                    "cannot be called. Data address: " + edr.dataAddress());
        }
        var builder = new Request.Builder().url(resolveUrl(edr.endpoint(), path));
        // The EDR token is passed through verbatim: the data plane issued it in whatever form it expects
        // back, so re-wrapping it (for example forcing a "Bearer " prefix) would corrupt it.
        if (edr.authorization() != null && !edr.authorization().isBlank()) {
            builder.header("Authorization", "Bearer " + edr.authorization());
        }
        headers.forEach(builder::header);
        return builder;
    }

    private static DataPlaneResponse execute(Request request) {
        try (var response = HTTP_CLIENT.newCall(request).execute()) {
            var body = response.body() != null ? response.body().string() : "";
            return new DataPlaneResponse(response.code(), body, response.headers().toMultimap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
