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

package org.eclipse.dataspacetck.cx.dcp.profile;

import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_PREFIX;

public interface CxProfile {

    String SCOPE_TYPE = "org.eclipse.dspace.dcp.vc.type:";

    String MEMBERSHIP_CREDENTIAL_TYPE = "MembershipCredential";
    String BPN_CREDENTIAL_TYPE = "BpnCredential";
    String GOV_CREDENTIAL_TYPE = "DataExchangeGovernanceCredential";

    String OPERATION_READ = ":read";

    String MEMBERSHIP_SCOPE = SCOPE_TYPE + MEMBERSHIP_CREDENTIAL_TYPE + OPERATION_READ;
    String BPN_SCOPE = SCOPE_TYPE + BPN_CREDENTIAL_TYPE + OPERATION_READ;
    String GOV_SCOPE = SCOPE_TYPE + GOV_CREDENTIAL_TYPE + OPERATION_READ;

    String CX_TCK_PREFIX = TCK_PREFIX + ".cx";
}
