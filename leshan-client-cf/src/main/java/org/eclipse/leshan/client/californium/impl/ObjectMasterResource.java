package org.eclipse.leshan.client.californium.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.leshan.client.resource.LinkFormattable;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectMasterResource extends CoapResource implements LinkFormattable {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectMasterResource.class);

    private final LwM2mObjectEnabler nodeEnabler;
    private final Map<InetAddress, ObjectResource> serverResources;

    private final InetSocketAddress managementAddress;

    public ObjectMasterResource(final LwM2mObjectEnabler nodeEnabler, final InetSocketAddress bootstrapAddress,
            final InetSocketAddress managementAddress) {
        super(Integer.toString(nodeEnabler.getId()));
        setObservable(true);

        serverResources = new HashMap<>();
        serverResources.put(managementAddress.getAddress(), new ObjectResource(nodeEnabler));

        if (!managementAddress.equals(bootstrapAddress) && bootstrapAddress != null) {
            serverResources.put(bootstrapAddress.getAddress(), new ObjectBootstrapResource(nodeEnabler));
        }

        this.managementAddress = managementAddress;

        this.nodeEnabler = nodeEnabler;
        this.nodeEnabler.setNotifySender(serverResources.get(managementAddress.getAddress()));
    }

    @Override
    public void handleRequest(final Exchange exchange) {
        final InetAddress sourceAddress = exchange.getRequest().getSource();
        if (serverResources.containsKey(sourceAddress)) {
            serverResources.get(sourceAddress).handleRequest(exchange);
        } else {
            // Let's just respond bad request for now
            exchange.sendResponse(new Response(ResponseCode.BAD_REQUEST));
        }
    }

    @Override
    public String asLinkFormat() {
        // TODO fix this when the rest of the link format stuff gets cleaned. Can probably remove the managementAddress
        // member.
        return serverResources.get(managementAddress.getAddress()).asLinkFormat();
    }

    /*
     * Override the default behavior so that requests to sub resources (typically /ObjectId/*) are handled by this
     * resource.
     */
    @Override
    public Resource getChild(final String name) {
        return this;
    }
}
