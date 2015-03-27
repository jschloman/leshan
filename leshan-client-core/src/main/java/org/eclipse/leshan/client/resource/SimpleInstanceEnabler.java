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
package org.eclipse.leshan.client.resource;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.Value;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;

public class SimpleInstanceEnabler extends BaseInstanceEnabler {

    protected Map<Integer, LwM2mResource> resources = new HashMap<Integer, LwM2mResource>();

    @Override
    public ValueResponse read(final int resourceid) {
        if (resources.containsKey(resourceid)) {
            return new ValueResponse(ResponseCode.CONTENT, resources.get(resourceid));
        }
        return new ValueResponse(ResponseCode.NOT_FOUND);
    }

    @Override
    public LwM2mResponse write(final int resourceid, final LwM2mResource value) {
        final LwM2mResource previousValue = resources.get(resourceid);
        resources.put(resourceid, value);
        if (!value.equals(previousValue))
            fireResourceChange(resourceid);
        return new LwM2mResponse(ResponseCode.CHANGED);
    }

    @Override
    public LwM2mResponse execute(final int resourceid, final byte[] params) {
        System.out.println("Execute on resource " + resourceid + " params " + params);
        return new LwM2mResponse(ResponseCode.CHANGED);
    }

    @Override
    public void setObjectModel(final ObjectModel objectModel) {
        super.setObjectModel(objectModel);

        // initialize resources
        for (final ResourceModel resourceModel : objectModel.resources.values()) {
            if (resourceModel.operations.isReadable()) {
                final LwM2mResource newResource = createResource(objectModel, resourceModel);
                if (newResource != null) {
                    resources.put(newResource.getId(), newResource);
                }
            }
        }
    }

    protected LwM2mResource createResource(final ObjectModel objectModel, final ResourceModel resourceModel) {
        if (!resourceModel.multiple) {
            Value<?> value;
            switch (resourceModel.type) {
            case STRING:
                value = createDefaultStringValue(objectModel, resourceModel);
                break;
            case BOOLEAN:
                value = createDefaultBooleanValue(objectModel, resourceModel);
                break;
            case INTEGER:
                value = createDefaultIntegerValue(objectModel, resourceModel);
                break;
            case FLOAT:
                value = createDefaultFloatValue(objectModel, resourceModel);
                break;
            case TIME:
                value = createDefaultDateValue(objectModel, resourceModel);
                break;
            case OPAQUE:
                value = createDefaultOpaqueValue(objectModel, resourceModel);
                break;
            default:
                // this should not happened
                value = null;
                break;
            }
            if (value != null)
                return new LwM2mResource(resourceModel.id, value);
        } else {
            Value<?>[] values;
            switch (resourceModel.type) {
            case STRING:
                values = new Value[] { createDefaultStringValue(objectModel, resourceModel) };
                break;
            case BOOLEAN:
                values = new Value[] { createDefaultBooleanValue(objectModel, resourceModel),
                                        createDefaultBooleanValue(objectModel, resourceModel) };
                break;
            case INTEGER:
                values = new Value[] { createDefaultIntegerValue(objectModel, resourceModel),
                                        createDefaultIntegerValue(objectModel, resourceModel) };
                break;
            case FLOAT:
                values = new Value[] { createDefaultFloatValue(objectModel, resourceModel),
                                        createDefaultFloatValue(objectModel, resourceModel) };
                break;
            case TIME:
                values = new Value[] { createDefaultDateValue(objectModel, resourceModel) };
                break;
            case OPAQUE:
                values = new Value[] { createDefaultOpaqueValue(objectModel, resourceModel) };
                break;
            default:
                // this should not happened
                values = null;
                break;
            }
            if (values != null)
                return new LwM2mResource(resourceModel.id, values);
        }
        return null;
    }

    protected Value<String> createDefaultStringValue(final ObjectModel objectModel, final ResourceModel resourceModel) {
        return Value.newStringValue(resourceModel.name);
    }

    protected Value<Integer> createDefaultIntegerValue(final ObjectModel objectModel, final ResourceModel resourceModel) {
        return Value.newIntegerValue((int) (Math.random() * 100 % 101));
    }

    protected Value<Boolean> createDefaultBooleanValue(final ObjectModel objectModel, final ResourceModel resourceModel) {
        return Value.newBooleanValue(Math.random() * 100 % 2 == 0);
    }

    protected Value<?> createDefaultDateValue(final ObjectModel objectModel, final ResourceModel resourceModel) {
        return Value.newDateValue(new Date());
    }

    protected Value<?> createDefaultFloatValue(final ObjectModel objectModel, final ResourceModel resourceModel) {
        return Value.newFloatValue((float) Math.random() * 100);
    }

    protected Value<?> createDefaultOpaqueValue(final ObjectModel objectModel, final ResourceModel resourceModel) {
        return Value.newBinaryValue(new String("Default " + resourceModel.name).getBytes());
    }
}
