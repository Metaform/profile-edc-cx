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

package org.eclipse.dataspacetck.cx.usecase.schema;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates use-case application payloads against JSON Schema documents shipped on the classpath.
 * <p>
 * Use-case standards specify their messages as JSON Schema, so "is this message conformant" is a schema
 * question. This is kept separate from the DSP message validators the harness registers: those are wired
 * into the protocol pipelines and only reachable while a launcher is running, whereas a use-case suite
 * also needs to check payloads in plain unit tests - for instance to prove its own fixtures are valid
 * before ever contacting a system under test.
 * <p>
 * Schemas are resolved as {@code classpath:} resources, so a document may {@code $ref} a sibling by
 * relative path. Referencing a schema over the network is deliberately not supported: a conformance run
 * must not depend on an external host being reachable, and an in-cluster run may have no egress at all.
 */
public final class JsonSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    // Each document declares its own $schema; this is only the fallback for one that does not. The
    // certificate model is published as draft-04 while the message envelopes around it are 2020-12, so
    // the registry has to honour per-document dialects rather than force one.
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private JsonSchemas() {
    }

    /**
     * Validates a JSON document, returning one message per violation.
     *
     * @param schemaClasspath the classpath location of the schema, e.g. {@code /ccm/v240/schema/x.json}
     * @param json            the document to validate
     * @return the violations, empty if the document is valid
     */
    public static List<String> violations(String schemaClasspath, String json) {
        return violations(schemaClasspath, MAPPER.readTree(json));
    }

    /**
     * Validates an already-parsed JSON document, returning one message per violation.
     *
     * @param schemaClasspath the classpath location of the schema
     * @param json            the document to validate
     * @return the violations, empty if the document is valid
     */
    public static List<String> violations(String schemaClasspath, JsonNode json) {
        return schema(schemaClasspath).validate(json).stream()
                .map(JsonSchemas::describe)
                .toList();
    }

    /**
     * Serializes a value and validates the result.
     *
     * @param schemaClasspath the classpath location of the schema
     * @param value           the value to serialize and validate
     * @return the violations, empty if the serialized value is valid
     */
    public static List<String> violationsOf(String schemaClasspath, Object value) {
        return violations(schemaClasspath, MAPPER.valueToTree(value));
    }

    /**
     * Whether a document satisfies the schema.
     *
     * @param schemaClasspath the classpath location of the schema
     * @param json            the document to validate
     * @return true if there are no violations
     */
    public static boolean isValid(String schemaClasspath, String json) {
        return violations(schemaClasspath, json).isEmpty();
    }

    private static Schema schema(String schemaClasspath) {
        return CACHE.computeIfAbsent(schemaClasspath,
                location -> REGISTRY.getSchema(SchemaLocation.of("classpath:" + location)));
    }

    private static String describe(Error error) {
        var location = error.getInstanceLocation().toString();
        return location.isEmpty() ? error.getMessage() : location + ": " + error.getMessage();
    }
}
