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

package org.eclipse.dataspacetck.cx.system.dsp.catalog.http;

import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.dsp.system.api.client.catalog.CatalogClient;

import java.util.Map;

public class CxDspCatalogHttpClient implements CxDspCatalogClient {

    private final CatalogClient catalogClient;

    public CxDspCatalogHttpClient(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public Map<String, Object> getCatalog(Map<String, Object> message, boolean expectError) {
        return catalogClient.getCatalog(message, expectError);
    }

    @Override
    public Map<String, Object> getDataset(String datasetId, boolean expectError) {
        return catalogClient.getDataset(datasetId, expectError);
    }
}
