/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.dataspacetck.cx.suite;

import org.eclipse.dataspacetck.core.system.ConsoleMonitor;
import org.eclipse.dataspacetck.cx.system.CxSystemLauncher;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_LAUNCHER;

class CxTckSuiteRemoteTest {

    @Disabled
    @Test
    void verifyTestSuite() {
        var result = TckRuntime.Builder.newInstance()
                .property(TCK_LAUNCHER, CxSystemLauncher.class.getName())
                .property("dataspacetck.dsp.connector.http.url", "http://controlplane.edc-v.svc.cluster.local:8082/api/dsp/30df151f50d44839a089d807e700a8f3/cx-neptune")
                .property("dataspacetck.dsp.connector.http.base.url", "http://controlplane.edc-v.svc.cluster.local:8082/api/dsp/30df151f50d44839a089d807e700a8f3")
                .property("dataspacetck.dsp.connector.negotiation.initiate.url", "http://example.org")
                .property("dataspacetck.dsp.connector.transfer.initiate.url", "http://example.org")
                .property("dataspacetck.dsp.connector.agent.id", "urn:connector:provider")
                .property("dataspacetck.did.issuer", "did:web:cx-tck.edc-v.svc.cluster.local:issuer")
                .property("dataspacetck.did.holder", "did:web:cx-tck.edc-v.svc.cluster.local:cx-tck")
                .property("dataspacetck.key.holder", "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"deK8PmxHcQhUxgtyd2Cq-Edcif1APkxohKzS7FZsBWo\",\"y\":\"EhUBt_1EtjN4xCBAG5HQlGzmizbyLRY6kDRhJPKkoeo\",\"d\":\"C35yL0bFQgP2S1m3Wr5hDMV6iO8I1zCYNMGstdYAaz4\",\"kid\":\"test-1\",\"use\":\"sig\",\"alg\":\"ES256\"}")
                .property("dataspacetck.key.issuer", "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"deK8PmxHcQhUxgtyd2Cq-Edcif1APkxohKzS7FZsBWo\",\"y\":\"EhUBt_1EtjN4xCBAG5HQlGzmizbyLRY6kDRhJPKkoeo\",\"d\":\"C35yL0bFQgP2S1m3Wr5hDMV6iO8I1zCYNMGstdYAaz4\",\"kid\":\"test-1\",\"use\":\"sig\",\"alg\":\"ES256\"}")
                .property("dataspacetck.did.verifier", "did:web:identityhub.edc-v.svc.cluster.local%3A7083:provider")
                .property("dataspacetck.callback.address", "http://cx-tck.edc-v.svc.cluster.local")
                .property("dataspacetck.cx.bpn", "BPNL00000003TCK")
                .property("CX_CAT_01_01_DATASETID", "cert_asset")
                .property("CX_CAT_01_04_DATASETID", "bpn_asset")
                .property("CX_CAT_01_05_DATASETID", "bpn_asset")
                .property("CX_FLOW_01_01_DATASETID", "cert_asset")
                .property("CX_FLOW_01_01_FORMAT", "HttpData-PULL")
                .property("CX_FLOW_01_02_DATASETID", "gov_asset")
                .addPackage("org.eclipse.dataspacetck.cx.verification.flow")
                .monitor(new ConsoleMonitor(false, true))
                .build().execute();

        assertThat(result.getFailures()).isEmpty();
    }
}
