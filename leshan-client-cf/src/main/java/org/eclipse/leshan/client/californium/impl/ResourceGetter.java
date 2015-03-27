package org.eclipse.leshan.client.californium.impl;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.leshan.core.node.LwM2mNodeVisitor;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mResource;

public class ResourceGetter implements LwM2mNodeVisitor {

    private final List<LwM2mResource> resources;

    ResourceGetter() {
        resources = new LinkedList<>();
    }

    @Override
    public void visit(final LwM2mObject object) {
        for (final LwM2mObjectInstance oi : object.getInstances().values()) {
            oi.accept(this);
        }
    }

    @Override
    public void visit(final LwM2mObjectInstance instance) {
        resources.addAll(instance.getResources().values());
    }

    @Override
    public void visit(final LwM2mResource resource) {
        resources.add(resource);
    }

    public LwM2mResource[] getResources() {
        return resources.toArray(new LwM2mResource[] {});
    }

}
