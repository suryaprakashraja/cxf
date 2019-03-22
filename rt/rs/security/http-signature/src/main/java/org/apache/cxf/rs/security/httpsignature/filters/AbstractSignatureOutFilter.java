/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.rs.security.httpsignature.filters;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.rs.security.httpsignature.HTTPSignatureConstants;
import org.apache.cxf.rs.security.httpsignature.MessageSigner;
import org.apache.cxf.rs.security.httpsignature.exception.SignatureException;
import org.apache.cxf.rs.security.httpsignature.utils.DefaultSignatureConstants;
import org.apache.cxf.rs.security.httpsignature.utils.KeyManagementUtils;

/**
 * RS CXF abstract Filter which signs outgoing messages. It does not create a digest header
 */
abstract class AbstractSignatureOutFilter {
    private static final Logger LOG = LogUtils.getL7dLogger(AbstractSignatureOutFilter.class);

    private MessageSigner messageSigner;
    private boolean enabled;

    AbstractSignatureOutFilter() {
        this.enabled = true;
    }

    protected void performSignature(MultivaluedMap<String, Object> headers, String uriPath, String httpMethod) {
        if (!enabled) {
            LOG.fine("Create signature filter is disabled");
            return;
        }

        if (messageSigner == null) {
            messageSigner = createMessageSigner();
        }

        if (headers.containsKey("Signature")) {
            LOG.fine("Message already contains a signature");
            return;
        }

        LOG.fine("Starting filter message signing process");
        Map<String, List<String>> convertedHeaders = convertHeaders(headers);
        try {
            messageSigner.sign(convertedHeaders, uriPath, httpMethod);
        } catch (IOException ex) {
            throw new BadRequestException(ex);
        }
        headers.put("Signature", Collections.singletonList(convertedHeaders.get("Signature").get(0)));
        LOG.fine("Finished filter message verification process");
    }

    // Convert the headers from List<Object> -> List<String>
    private Map<String, List<String>> convertHeaders(MultivaluedMap<String, Object> requestHeaders) {
        Map<String, List<String>> convertedHeaders = new HashMap<>(requestHeaders.size());
        for (Map.Entry<String, List<Object>> entry : requestHeaders.entrySet()) {
            convertedHeaders.put(entry.getKey(),
                                 entry.getValue().stream().map(o -> o.toString()).collect(Collectors.toList()));
        }
        return convertedHeaders;
    }

    public MessageSigner getMessageSigner() {
        return messageSigner;
    }

    public void setMessageSigner(MessageSigner messageSigner) {
        Objects.requireNonNull(messageSigner);
        this.messageSigner = messageSigner;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private MessageSigner createMessageSigner() {
        Properties props = KeyManagementUtils.loadSignatureOutProperties();
        if (props == null) {
            throw new SignatureException("Signature properties are not configured correctly");
        }

        Message m = PhaseInterceptorChain.getCurrentMessage();
        PrivateKey privateKey = KeyManagementUtils.loadPrivateKey(m, props);

        String signatureAlgorithm = (String)m.getContextualProperty(HTTPSignatureConstants.RSSEC_SIGNATURE_ALGORITHM);
        if (signatureAlgorithm == null) {
            signatureAlgorithm = DefaultSignatureConstants.SIGNING_ALGORITHM;
        }

        return new MessageSigner(signatureAlgorithm, DefaultSignatureConstants.DIGEST_ALGORITHM, privateKey,
                                 "some-key-id", true, Collections.emptyList());
    }

}
