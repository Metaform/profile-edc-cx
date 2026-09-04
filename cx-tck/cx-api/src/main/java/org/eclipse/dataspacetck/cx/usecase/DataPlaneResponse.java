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

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * The outcome of an application-API call made through a data plane.
 * <p>
 * The status code and the raw body are both kept, and a non-success code is <em>not</em> an error here:
 * use-case standards specify status codes normatively (a certificate request answers 202 while it is in
 * progress and 200 once it is not), so distinguishing them is the assertion, not a failure.
 *
 * @param code    the HTTP status code
 * @param body    the response body, empty if the response carried none
 * @param headers the response headers, keyed by name
 */
public record DataPlaneResponse(int code, String body, Map<String, List<String>> headers) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Whether the status code is in the 2xx range.
     *
     * @return true for 200..299
     */
    public boolean successful() {
        return code >= 200 && code < 300;
    }

    /**
     * Parses the body as a JSON object.
     *
     * @return the parsed body
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap() {
        return MAPPER.readValue(body, Map.class);
    }

    /**
     * Parses the body into the given type.
     *
     * @param type the target type
     * @param <T>  the target type
     * @return the parsed body
     */
    public <T> T as(Class<T> type) {
        return MAPPER.readValue(body, type);
    }

    /**
     * A short description of the response, for use in assertion messages.
     *
     * @return the status code and body
     */
    public String describe() {
        return "HTTP %d: %s".formatted(code, body == null || body.isBlank() ? "<empty body>" : body);
    }
}
