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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;

public class ObjectsInitializer {

    protected Map<Integer, Class<? extends LwM2mInstanceEnabler>> classes = new HashMap<Integer, Class<? extends LwM2mInstanceEnabler>>();
    protected LwM2mModel model;

    public ObjectsInitializer() {
        this(null);
    }

    public ObjectsInitializer(final LwM2mModel model) {
        if (model == null) {
            final List<ObjectModel> objects = ObjectLoader.loadDefault();
            final HashMap<Integer, ObjectModel> map = new HashMap<Integer, ObjectModel>();
            for (final ObjectModel objectModel : objects) {
                map.put(objectModel.id, objectModel);
            }
            this.model = new LwM2mModel(map);
        } else {
            this.model = model;
        }
    }

    public void setClassForObject(final int objectId, final Class<? extends LwM2mInstanceEnabler> clazz) {
        classes.put(objectId, clazz);
    }

    public List<ObjectEnabler> createMandatory() {
        final Collection<ObjectModel> objectModels = model.getObjectModels();

        final List<ObjectEnabler> enablers = new ArrayList<ObjectEnabler>();
        for (final ObjectModel objectModel : objectModels) {
            if (objectModel.mandatory) {
                final ObjectEnabler objectEnabler = createNodeEnabler(objectModel);
                if (objectEnabler != null)
                    enablers.add(objectEnabler);
            }
        }
        return enablers;
    }

    public List<ObjectEnabler> create(final int... objectId) {
        final List<ObjectEnabler> enablers = new ArrayList<ObjectEnabler>();
        for (int i = 0; i < objectId.length; i++) {
            final ObjectModel objectModel = model.getObjectModel(objectId[i]);
            final ObjectEnabler objectEnabler = createNodeEnabler(objectModel);
            if (objectEnabler != null)
                enablers.add(objectEnabler);

        }
        return enablers;
    }

    protected ObjectEnabler createNodeEnabler(final ObjectModel objectModel) {
        final HashMap<Integer, LwM2mInstanceEnabler> instances = new HashMap<Integer, LwM2mInstanceEnabler>();

        if (!objectModel.multiple) {
            final LwM2mInstanceEnabler newInstance = createInstance(objectModel);
            if (newInstance != null) {
                instances.put(0, newInstance);
                return new ObjectEnabler(objectModel.id, objectModel, instances, SimpleInstanceEnabler.class);
            }
        }
        // This is an overstatement and we need to have one for the
        if (classes.containsKey(objectModel.id)) {
            return new ObjectEnabler(objectModel.id, objectModel, instances, classes.get(objectModel.id));
        }
        return new ObjectEnabler(objectModel.id, objectModel, instances, SimpleInstanceEnabler.class);
    }

    protected LwM2mInstanceEnabler createInstance(final ObjectModel objectModel) {
        Class<? extends LwM2mInstanceEnabler> clazz = classes.get(objectModel.id);
        if (clazz == null)
            clazz = SimpleInstanceEnabler.class;

        LwM2mInstanceEnabler instance;
        try {
            instance = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        instance.setObjectModel(objectModel);
        return instance;
    }
}
