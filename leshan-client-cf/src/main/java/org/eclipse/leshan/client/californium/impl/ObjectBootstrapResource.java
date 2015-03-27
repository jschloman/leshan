/*******************************************************************************
 * Copyright (c) 2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client.californium.impl;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.codec.InvalidValueException;
import org.eclipse.leshan.core.node.codec.LwM2mNodeDecoder;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CoAP {@link Resource} in charge of handling Bootstrap requests for of a lwM2M Object.
 */
public class ObjectBootstrapResource extends ObjectResource {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectBootstrapResource.class);

    public ObjectBootstrapResource(final LwM2mObjectEnabler nodeEnabler) {
        super(nodeEnabler.getId(), nodeEnabler);
    }

    @Override
    public void handlePUT(final CoapExchange coapExchange) {
        final String URI = coapExchange.getRequestOptions().getUriPathString();
        LOG.debug("Received a PUT on " + getName() + " from " + coapExchange.getSourceAddress() + ":"
                + coapExchange.getSourcePort());

        // Manage Write Request (replace)
        final LwM2mPath path = new LwM2mPath(URI);
        final ContentFormat contentFormat = ContentFormat.fromCode(coapExchange.getRequestOptions().getContentFormat());
        LwM2mNode lwM2mNode;
        try {
            final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
            lwM2mNode = LwM2mNodeDecoder.decode(coapExchange.getRequestPayload(), contentFormat, path, model);
            LwM2mResponse response = nodeEnabler.write(new WriteRequest(URI, lwM2mNode, contentFormat, true));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.NOT_FOUND) {
                LOG.debug("Nodes not found.  Executing a Create as specificed in the Specification.");
                final ResourceGetter resourceGetter = new ResourceGetter();
                lwM2mNode.accept(resourceGetter);
                final LwM2mResource[] resources = resourceGetter.getResources();

                response = nodeEnabler.create(new CreateRequest(URI, resources, contentFormat));
            }

            coapExchange.respond(fromLwM2mCode(response.getCode()));
            return;
        } catch (final InvalidValueException e) {
            LOG.debug("Received an Invalid Write on the path " + e.getPath());
            coapExchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            return;
        }

    }

    @Override
    public void handleDELETE(final CoapExchange coapExchange) {
        LOG.debug("Received a DELETE on " + getName());
        // Manage Delete Request
        final String URI = coapExchange.getRequestOptions().getUriPathString();
        final LwM2mResponse response = nodeEnabler.delete(new DeleteRequest(URI));
        coapExchange.respond(fromLwM2mCode(response.getCode()));
    }

}
