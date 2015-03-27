package org.eclipse.leshan.client.resource;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.core.node.LwM2mPath;

public interface InstanceChangedListener {

    void instanceChanged(LwM2mPath path, ResponseCode code);

}
