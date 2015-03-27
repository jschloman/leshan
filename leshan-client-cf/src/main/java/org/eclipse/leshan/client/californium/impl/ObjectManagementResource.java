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

import java.util.List;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.leshan.ObserveSpec;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.util.ObserveSpecParser;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.codec.InvalidValueException;
import org.eclipse.leshan.core.node.codec.LwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.LwM2mNodeEncoder;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.ContentFormatHelper;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.DiscoverRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteAttributesRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.CreateResponse;
import org.eclipse.leshan.core.response.DiscoverResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;

/**
 * A CoAP {@link Resource} in charge of handling Device Management requests for of a lwM2M Object.
 */
public class ObjectManagementResource extends ObjectResource {

    public ObjectManagementResource(final LwM2mObjectEnabler nodeEnabler) {
        super(nodeEnabler.getId(), nodeEnabler);

        this.nodeEnabler.setNotifySender(this);
    }

    @Override
    public void handleGET(final CoapExchange exchange) {
        final String URI = exchange.getRequestOptions().getUriPathString();

        // Manage Discover Request
        if (exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {
            final DiscoverResponse response = nodeEnabler.discover(new DiscoverRequest(URI));
            exchange.respond(fromLwM2mCode(response.getCode()), LinkFormatUtils.payloadize(response.getObjectLinks()),
                    MediaTypeRegistry.APPLICATION_LINK_FORMAT);
        }
        // Manage Observe Request
        else if (exchange.getRequestOptions().hasObserve()) {
            final ValueResponse response = nodeEnabler.observe(new ObserveRequest(URI));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.CONTENT) {
                final LwM2mPath path = new LwM2mPath(URI);
                final LwM2mNode content = response.getContent();
                final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
                final ContentFormat contentFormat = ContentFormatHelper.compute(path, content, model);
                exchange.respond(ResponseCode.CONTENT, LwM2mNodeEncoder.encode(content, contentFormat, path, model));
                return;
            } else {
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            }
        }
        // Manage Read Request
        else {
            final ValueResponse response = nodeEnabler.read(new ReadRequest(URI));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.CONTENT) {
                final LwM2mPath path = new LwM2mPath(URI);
                final LwM2mNode content = response.getContent();
                final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
                final ContentFormat contentFormat = ContentFormatHelper.compute(path, content, model);
                exchange.respond(ResponseCode.CONTENT, LwM2mNodeEncoder.encode(content, contentFormat, path, model));
                return;
            } else {
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            }
        }
    }

    @Override
    public void handlePUT(final CoapExchange coapExchange) {
        final String URI = coapExchange.getRequestOptions().getUriPathString();

        // get Observe Spec
        ObserveSpec spec = null;
        if (coapExchange.advanced().getRequest().getOptions().getURIQueryCount() != 0) {
            final List<String> uriQueries = coapExchange.advanced().getRequest().getOptions().getUriQuery();
            spec = ObserveSpecParser.parse(uriQueries);
        }

        // Manage Write Attributes Request
        if (spec != null) {
            final LwM2mResponse response = nodeEnabler.writeAttributes(new WriteAttributesRequest(URI, spec));
            coapExchange.respond(fromLwM2mCode(response.getCode()));
            return;
        }
        // Manage Write Request (replace)
        else {
            final LwM2mPath path = new LwM2mPath(URI);
            final ContentFormat contentFormat = ContentFormat.fromCode(coapExchange.getRequestOptions()
                    .getContentFormat());
            LwM2mNode lwM2mNode;
            try {
                final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
                lwM2mNode = LwM2mNodeDecoder.decode(coapExchange.getRequestPayload(), contentFormat, path, model);
                final LwM2mResponse response = nodeEnabler.write(new WriteRequest(URI, lwM2mNode, contentFormat, true));
                coapExchange.respond(fromLwM2mCode(response.getCode()));
                return;
            } catch (final InvalidValueException e) {
                coapExchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
                return;
            }

        }
    }

    @Override
    public void handlePOST(final CoapExchange exchange) {
        final String URI = exchange.getRequestOptions().getUriPathString();
        final LwM2mPath path = new LwM2mPath(URI);

        // Manage Execute Request
        if (path.isResource()) {
            final LwM2mResponse response = nodeEnabler.execute(new ExecuteRequest(URI));
            exchange.respond(fromLwM2mCode(response.getCode()));
            return;
        }

        // Manage Create Request
        try {
            final ContentFormat contentFormat = ContentFormat.fromCode(exchange.getRequestOptions().getContentFormat());
            final LwM2mModel model = new LwM2mModel(nodeEnabler.getObjectModel());
            final LwM2mNode lwM2mNode = LwM2mNodeDecoder.decode(exchange.getRequestPayload(), contentFormat, path,
                    model);
            if (!(lwM2mNode instanceof LwM2mObjectInstance)) {
                exchange.respond(ResponseCode.BAD_REQUEST);
                return;
            }
            final LwM2mResource[] resources = ((LwM2mObjectInstance) lwM2mNode).getResources().values()
                    .toArray(new LwM2mResource[0]);
            final CreateResponse response = nodeEnabler.create(new CreateRequest(URI, resources, contentFormat));
            if (response.getCode() == org.eclipse.leshan.ResponseCode.CREATED) {
                exchange.setLocationPath(response.getLocation());
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            } else {
                exchange.respond(fromLwM2mCode(response.getCode()));
                return;
            }
        } catch (final InvalidValueException e) {
            exchange.respond(ResponseCode.BAD_REQUEST);
            return;
        }
    }

    @Override
    public void handleDELETE(final CoapExchange coapExchange) {
        // Manage Delete Request
        final String URI = coapExchange.getRequestOptions().getUriPathString();
        final LwM2mResponse response = nodeEnabler.delete(new DeleteRequest(URI));
        coapExchange.respond(fromLwM2mCode(response.getCode()));
    }

}
