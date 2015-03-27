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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.CreateResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ValueResponse;

public class ObjectEnabler extends BaseObjectEnabler {

    // TODO we should manage that in a threadsafe way
    private final Map<Integer, LwM2mInstanceEnabler> instances;
    private final Class<? extends LwM2mInstanceEnabler> instanceClass;

    public ObjectEnabler(final int id, final ObjectModel objectModel,
            final Map<Integer, LwM2mInstanceEnabler> instances,
            final Class<? extends LwM2mInstanceEnabler> instanceClass) {
        super(id, objectModel);
        this.instances = new HashMap<Integer, LwM2mInstanceEnabler>(instances);
        this.instanceClass = instanceClass;
        for (final Entry<Integer, LwM2mInstanceEnabler> entry : this.instances.entrySet()) {
            listenInstance(entry.getValue(), entry.getKey());
        }
    }

    @Override
    protected CreateResponse doCreate(final CreateRequest request) {
        try {
            // TODO manage case where instanceid is not available
            final LwM2mInstanceEnabler newInstance = instanceClass.newInstance();
            newInstance.setObjectModel(getObjectModel());

            for (final LwM2mResource resource : request.getResources()) {
                newInstance.write(resource.getId(), resource);
            }
            instances.put(request.getPath().getObjectInstanceId(), newInstance);
            listenInstance(newInstance, request.getPath().getObjectInstanceId());

            fireInstanceChange(request.getPath(), ResponseCode.CREATED);
            return new CreateResponse(ResponseCode.CREATED, request.getPath().toString());
        } catch (InstantiationException | IllegalAccessException e) {
            // TODO not really a bad request ...
            return new CreateResponse(ResponseCode.BAD_REQUEST);
        }
    }

    @Override
    protected ValueResponse doRead(final ReadRequest request) {
        final LwM2mPath path = request.getPath();

        // Manage Object case
        if (path.isObject()) {
            final List<LwM2mObjectInstance> lwM2mObjectInstances = new ArrayList<>();
            for (final Entry<Integer, LwM2mInstanceEnabler> entry : instances.entrySet()) {
                lwM2mObjectInstances.add(getLwM2mObjectInstance(entry.getKey(), entry.getValue()));
            }
            return new ValueResponse(ResponseCode.CONTENT, new LwM2mObject(getId(),
                    lwM2mObjectInstances.toArray(new LwM2mObjectInstance[0])));
        }

        // Manage Instance case
        final LwM2mInstanceEnabler instance = instances.get(path.getObjectInstanceId());
        if (instance == null)
            return new ValueResponse(ResponseCode.NOT_FOUND);

        if (path.getResourceId() == null) {
            return new ValueResponse(ResponseCode.CONTENT, getLwM2mObjectInstance(path.getObjectInstanceId(), instance));
        }

        // Manage Resource case
        return instance.read(path.getResourceId());
    }

    LwM2mObjectInstance getLwM2mObjectInstance(final int instanceid, final LwM2mInstanceEnabler instance) {
        final List<LwM2mResource> resources = new ArrayList<>();
        for (final ResourceModel resourceModel : getObjectModel().resources.values()) {
            if (resourceModel.operations.isReadable()) {
                final ValueResponse response = instance.read(resourceModel.id);
                if (response.getCode() == ResponseCode.CONTENT && response.getContent() instanceof LwM2mResource)
                    resources.add((LwM2mResource) response.getContent());
            }
        }
        return new LwM2mObjectInstance(instanceid, resources.toArray(new LwM2mResource[0]));
    }

    @Override
    protected LwM2mResponse doWrite(final WriteRequest request) {
        final LwM2mPath path = request.getPath();

        // Manage Instance case
        final LwM2mInstanceEnabler instance = instances.get(path.getObjectInstanceId());
        if (instance == null)
            return new LwM2mResponse(ResponseCode.NOT_FOUND);

        if (path.getResourceId() == null) {
            for (final LwM2mResource resource : ((LwM2mObjectInstance) request.getNode()).getResources().values()) {
                instance.write(resource.getId(), resource);
            }

            fireInstanceChange(request.getPath(), ResponseCode.CHANGED);
            return new LwM2mResponse(ResponseCode.CHANGED);
        }

        // Manage Resource case
        fireInstanceChange(request.getPath(), ResponseCode.CHANGED);
        return instance.write(path.getResourceId(), (LwM2mResource) request.getNode());
    }

    @Override
    protected LwM2mResponse doExecute(final ExecuteRequest request) {
        final LwM2mPath path = request.getPath();
        final LwM2mInstanceEnabler instance = instances.get(path.getObjectInstanceId());
        if (instance == null) {
            return new LwM2mResponse(ResponseCode.NOT_FOUND);
        }
        return instance.execute(path.getResourceId(), request.getParameters());
    }

    @Override
    protected LwM2mResponse doDelete(final DeleteRequest request) {
        final LwM2mPath path = request.getPath();
        if (!instances.containsKey(path.getObjectInstanceId())) {
            return new LwM2mResponse(ResponseCode.NOT_FOUND);
        }
        instances.remove(request.getPath().getObjectInstanceId());

        fireInstanceChange(request.getPath(), ResponseCode.DELETED);

        return new LwM2mResponse(ResponseCode.DELETED);
    }

    private void listenInstance(final LwM2mInstanceEnabler instance, final int instanceId) {
        instance.addResourceChangedListener(new ResourceChangedListener() {
            @Override
            public void resourceChanged(final int resourceId) {
                getNotifySender().sendNotify(getId() + "/" + instanceId + "/" + resourceId);
            }
        });
    }

    public Map<Integer, LwM2mInstanceEnabler> getInstances() {
        return Collections.unmodifiableMap(instances);
    }

}
