package org.eclipse.leshan.client.californium.impl;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObserveRelationContainer;
import org.eclipse.leshan.client.resource.LinkFormattable;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.NotifySender;
import org.eclipse.leshan.util.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ObjectResource extends CoapResource implements LinkFormattable, NotifySender {
    static final Logger LOG = LoggerFactory.getLogger(ObjectResource.class);

    protected final LwM2mObjectEnabler nodeEnabler;

    public ObjectResource(final int objectId, final LwM2mObjectEnabler nodeEnabler) {
        super(Integer.toString(objectId));

        this.nodeEnabler = nodeEnabler;
    }

    @Override
    public void handleRequest(final Exchange exchange) {
        try {
            super.handleRequest(exchange);
        } catch (final Exception e) {
            LOG.error("Exception while handling a request on the /" + getName() + " resource", e);
            // unexpected error, we should sent something like a INTERNAL_SERVER_ERROR.
            // but it would not be LWM2M compliant. so BAD_REQUEST for now...
            exchange.sendResponse(new Response(ResponseCode.BAD_REQUEST));
        }
    }

    public static ResponseCode fromLwM2mCode(final org.eclipse.leshan.ResponseCode code) {
        Validate.notNull(code);

        switch (code) {
        case CREATED:
            return ResponseCode.CREATED;
        case DELETED:
            return ResponseCode.DELETED;
        case CHANGED:
            return ResponseCode.CHANGED;
        case CONTENT:
            return ResponseCode.CONTENT;
        case BAD_REQUEST:
            return ResponseCode.BAD_REQUEST;
        case UNAUTHORIZED:
            return ResponseCode.UNAUTHORIZED;
        case NOT_FOUND:
            return ResponseCode.NOT_FOUND;
        case METHOD_NOT_ALLOWED:
            return ResponseCode.METHOD_NOT_ALLOWED;
        case FORBIDDEN:
            return ResponseCode.FORBIDDEN;
        default:
            throw new IllegalArgumentException("Invalid CoAP code for LWM2M response: " + code);
        }
    }

    @Override
    public void sendNotify(final String URI) {
        notifyObserverRelationsForResource(URI);
    }

    @Override
    public String asLinkFormat() {
        final StringBuilder linkFormat = LinkFormat.serializeResource(this).append(
                LinkFormat.serializeAttributes(getAttributes()));
        linkFormat.deleteCharAt(linkFormat.length() - 1);
        return linkFormat.toString();
    }

    /*
     * TODO: Observe HACK we should see if this could not be integrated in californium
     * http://dev.eclipse.org/mhonarc/lists/cf-dev/msg00181.html
     */
    final ObserveRelationContainer observeRelations = new ObserveRelationContainer();

    @Override
    public void addObserveRelation(final ObserveRelation relation) {
        super.addObserveRelation(relation);
        observeRelations.add(relation);
    }

    @Override
    public void removeObserveRelation(final ObserveRelation relation) {
        super.removeObserveRelation(relation);
        observeRelations.remove(relation);
    }

    protected void notifyObserverRelationsForResource(final String URI) {
        for (final ObserveRelation relation : observeRelations) {
            if (relation.getExchange().getRequest().getOptions().getUriPathString().equals(URI)) {
                relation.notifyObservers();
            }
        }
    }

}