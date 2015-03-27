package org.eclipse.leshan.integration.tests;

import java.util.Map;

import org.eclipse.leshan.client.resource.SimpleInstanceEnabler;
import org.eclipse.leshan.core.node.LwM2mResource;

public class TestingInstanceEnabler extends SimpleInstanceEnabler {
    public TestingInstanceEnabler() {
    }

    public Map<Integer, LwM2mResource> getResources() {
        return resources;
    }
}
