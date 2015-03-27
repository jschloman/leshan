/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
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
package org.eclipse.leshan.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.Validate;
import org.eclipse.leshan.bootstrap.ConfigurationChecker.ConfigurationException;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig;
import org.eclipse.leshan.server.bootstrap.BootstrapStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple bootstrap store implementation storing bootstrap information in memory
 */
public class BootstrapStoreImpl implements BootstrapStore {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapStoreImpl.class);

    // default location for persistence
    private static final String DEFAULT_FILE = "data/bootstrap.data";

    private final String filename;

    public BootstrapStoreImpl() {
        this(DEFAULT_FILE);
    }

    /**
     * @param file the file path to persist the registry
     */
    public BootstrapStoreImpl(final String filename) {
        Validate.notEmpty(filename);

        this.filename = filename;
        this.loadFromFile();
    }

    private final Map<String, BootstrapConfig> bootstrapByEndpoint = new ConcurrentHashMap<>();

    @Override
    public BootstrapConfig getBootstrap(final String endpoint) {
        return bootstrapByEndpoint.get(endpoint);
    }

    public void addConfig(final String endpoint, final BootstrapConfig config) throws ConfigurationException {
        ConfigurationChecker.verify(config);
        // check the configuration
        bootstrapByEndpoint.put(endpoint, config);
        //
        // TODO save to JSON format
        // saveToFile();
    }

    public Map<String, BootstrapConfig> getBootstrapConfigs() {
        return Collections.unmodifiableMap(bootstrapByEndpoint);
    }

    public boolean deleteConfig(final String enpoint) {
        final BootstrapConfig res = bootstrapByEndpoint.remove(enpoint);
        saveToFile();
        return res != null;
    }

    // /////// File persistence

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        try {
            final File file = new File(filename);

            if (!file.exists()) {
                // create parents if needed
                final File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                file.createNewFile();

            } else {

                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                    bootstrapByEndpoint.putAll((Map<String, BootstrapConfig>) in.readObject());
                    LOG.error("Size of our store " + bootstrapByEndpoint.size());

                    for (final Entry<String, BootstrapConfig> e : bootstrapByEndpoint.entrySet()) {
                        LOG.error("Have key for '" + e.getKey() + "'");

                    }
                }
            }
        } catch (final FileNotFoundException e) {
            LOG.error("File not found?");
            // fine
        } catch (final Exception e) {
            LOG.error("Could not load bootstrap infos from file", e);
        }
    }

    private void saveToFile() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            final Map<String, BootstrapConfig> copy = new HashMap<>(bootstrapByEndpoint);
            out.writeObject(copy);
        } catch (final Exception e) {
            LOG.debug("Could not save bootstrap infos to file", e);
        }
    }

}
