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
 *     Zebra Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.integration.tests;

import static com.jayway.awaitility.Awaitility.await;
import static org.eclipse.leshan.integration.tests.IntegrationTestHelper.ENDPOINT_IDENTIFIER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.bootstrap.BootstrapStoreImpl;
import org.eclipse.leshan.bootstrap.ConfigurationChecker.ConfigurationException;
import org.eclipse.leshan.client.resource.InstanceChangedListener;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.Value;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.BootstrapRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.RegisterResponse;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig.ServerConfig;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig.ServerSecurity;
import org.eclipse.leshan.server.bootstrap.SecurityMode;
import org.eclipse.leshan.server.bootstrap.SmsSecurityMode;
import org.eclipse.leshan.server.impl.SecurityRegistryImpl;
import org.eclipse.leshan.server.security.NonUniqueSecurityInfoException;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.eclipse.leshan.tlv.Tlv;
import org.eclipse.leshan.tlv.Tlv.TlvType;
import org.eclipse.leshan.tlv.TlvEncoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.jayway.awaitility.Duration;

public class BootstrapTest {
    private static final String PRIVATE_KEY = "PRIVATE KEY";

    private static final String PUBLIC_KEY_OR_IDENTITY = "PUBLIC KEY";

    private static String STORED_ENDPOINT_IDENTIFIER = "SampleEndpointId";

    private final IntegrationTestHelper helper = new IntegrationTestHelper(true);

    static {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        // map slf4j to jul
        final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger("org.eclipse.californium.scandium");
        final java.util.logging.Logger julLogger = java.util.logging.Logger
                .getLogger("org.eclipse.californium.scandium");
        if (slf4jLogger.isTraceEnabled()) {
            julLogger.setLevel(Level.FINEST);
        } else if (slf4jLogger.isDebugEnabled()) {
            julLogger.setLevel(Level.FINER);
        } else if (slf4jLogger.isInfoEnabled()) {
            julLogger.setLevel(Level.FINE);
        } else if (slf4jLogger.isWarnEnabled()) {
            julLogger.setLevel(Level.WARNING);
        } else if (slf4jLogger.isErrorEnabled()) {
            julLogger.setLevel(Level.SEVERE);
        }
    }

    @Before
    public void start() {
        helper.start();
    }

    @After
    public void stop() {
        helper.stop();
    }

    // TODO this works but I want to ignore its output for now.
    @Ignore
    @Test
    public void bootstrap_without_store_entry_denied() {
        final LwM2mResponse response = helper.client.send(new BootstrapRequest(ENDPOINT_IDENTIFIER));

        assertEquals(ResponseCode.BAD_REQUEST, response.getCode());
    }

    // Because the TLV encoding writes out the smsNumber as a INTEGEr but the SecurityStore stores it as a String
    // We have conversion issues.
    @Test
    public void test_converstion_of_sms_number() {
        final String expected = "2323";
        final byte[] bytes = expected.getBytes();
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final int actual = buffer.getInt();
        final String actualString = new String(ByteBuffer.allocate(4).putInt(actual).array());
        assertEquals(expected, actualString);
    }

    @Test
    public void bootstrap_with_store_entry_changed() {
        // TODO creating a LeshanClient with a server address should probably fill its server object...
        // openssl genrsa -aes128 -out key.pem
        // TODO should deletion of servers result in endpoints also being destroyed?

        final AtomicInteger bootstrapCreateCount = new AtomicInteger(0);
        for (final ObjectEnabler enabler : helper.clientObjects) {
            enabler.addInstanceListener(new InstanceChangedListener() {

                @Override
                public void instanceChanged(final LwM2mPath path, final ResponseCode code) {
                    if (code == ResponseCode.CREATED) {
                        bootstrapCreateCount.incrementAndGet();
                    }
                }
            });
        }
        assertObjectResources(0, 0);
        assertObjectResources(1, 0);

        final LwM2mResponse response = helper.client.send(new BootstrapRequest(STORED_ENDPOINT_IDENTIFIER));

        // Now need to wait for the correct application of server/settings
        await().atMost(new Duration(10, TimeUnit.SECONDS)).untilAtomic(bootstrapCreateCount,
                org.hamcrest.Matchers.equalTo(2));

        assertEquals(ResponseCode.CHANGED, response.getCode());
        // TODO deletes are valid on root (i.e., '/') but the client then needs to override the RootResource to handle
        // such behavior.... but RootResource is a private internal class of CoapServer... :/

        assertObjectResources(1, 1);

        assertSecurityInstanceValues(0, 0, createServerSecurity());
        assertServerInstanceValues(1, 0, createServerConfig());

        // TODO should then register based upon the Security and Server retrieved.
        // We need to create a new secure endpoint on the CoapServer, attach the correct PSK keys, then use that to
        // connect up
        final RegisterResponse registerResponse = helper.client.send(new RegisterRequest(STORED_ENDPOINT_IDENTIFIER));
        assertEquals(ResponseCode.CREATED, registerResponse.getCode());
    }

    @SuppressWarnings(value = { "unchecked" })
    private void assertServerInstanceValues(final int objectId, final int instanceId, final ServerConfig expectedServer) {
        for (final ObjectEnabler enabler : helper.clientObjects) {
            if (enabler.getId() == objectId) {
                final TestingInstanceEnabler instanceEnabler = (TestingInstanceEnabler) enabler.getInstances().get(
                        instanceId);
                final Map<Integer, LwM2mResource> resources = instanceEnabler.getResources();
                final ServerConfig actualServer = new ServerConfig();
                actualServer.shortId = ((Value<Integer>) resources.get(0).getValue()).value;
                actualServer.lifetime = ((Value<Integer>) resources.get(1).getValue()).value;
                actualServer.defaultMinPeriod = ((Value<Integer>) resources.get(2).getValue()).value;
                actualServer.defaultMaxPeriod = ((Value<Integer>) resources.get(3).getValue()).value;
                actualServer.disableTimeout = resources.containsKey(4) == true ? ((Value<Integer>) resources.get(4)
                        .getValue()).value : 0;
                actualServer.notifyIfDisabled = ((Value<Boolean>) resources.get(6).getValue()).value;
                actualServer.binding = BindingMode.valueOf(((Value<String>) resources.get(7).getValue()).value);

                assertEquals(expectedServer, actualServer);

                return;
            }
        }
    }

    @SuppressWarnings(value = { "unchecked" })
    private void assertSecurityInstanceValues(final int objectId, final int instanceId,
            final ServerSecurity expectedSecurity) {
        for (final ObjectEnabler enabler : helper.clientObjects) {
            if (enabler.getId() == objectId) {
                final TestingInstanceEnabler instanceEnabler = (TestingInstanceEnabler) enabler.getInstances().get(
                        instanceId);
                // TODO let's get this out as TLV and we can just compare it to the TLV of the above security object
                // TODO maybe a objectmodellistener or something?
                final Map<Integer, LwM2mResource> resources = instanceEnabler.getResources();
                final ServerSecurity actualSecurity = new ServerSecurity();
                actualSecurity.uri = ((Value<String>) resources.get(0).getValue()).value;
                actualSecurity.bootstrapServer = ((Value<Boolean>) resources.get(1).getValue()).value;
                actualSecurity.securityMode = SecurityMode.values()[((Value<Integer>) resources.get(2).getValue()).value];
                actualSecurity.publicKeyOrId = ((Value<byte[]>) resources.get(3).getValue()).value;
                actualSecurity.serverPublicKeyOrId = ((Value<byte[]>) resources.get(4).getValue()).value;
                actualSecurity.secretKey = ((Value<byte[]>) resources.get(5).getValue()).value;
                actualSecurity.smsSecurityMode = SmsSecurityMode.values()[(((Value<Integer>) resources.get(6)
                        .getValue()).value)];
                actualSecurity.smsBindingKeyParam = ((Value<byte[]>) resources.get(7).getValue()).value;
                actualSecurity.smsBindingKeySecret = ((Value<byte[]>) resources.get(8).getValue()).value;
                // TODO this is ugly simply because ServerSecurity uses String and the TLVEncoder/Decoders use Integers.
                actualSecurity.serverSmsNumber = new String(ByteBuffer.allocate(4)
                        .putInt(((Value<Integer>) resources.get(9).getValue()).value).array());

                actualSecurity.serverId = ((Value<Integer>) resources.get(10).getValue()).value;
                actualSecurity.clientOldOffTime = ((Value<Integer>) resources.get(11).getValue()).value;

                assertEquals(expectedSecurity, actualSecurity);

                return;
            }
        }
    }

    private void assertObjectResources(final int objectId, final int expectedResources) {
        for (final ObjectEnabler enabler : helper.clientObjects) {
            if (enabler.getId() == objectId) {
                assertEquals(expectedResources, enabler.getInstances().values().size());
                return;
            }
        }
    }

    @Ignore
    @Test
    public void bootstrap_write_not_possible_from_nonbootstrap_server() {
        // TODO because the write-as-upcert should only happen when Bootstrapping, make sure a nefarious device
        // management server can't do that.
        fail("Not yet implemented so do this.");
    }

    public static void main(final String[] args) throws ConfigurationException, NonUniqueSecurityInfoException {
        // TODO probably need to add BouncyCastle for a real provider. Now simply for dev purposes.

        final BootstrapStoreImpl store = new BootstrapStoreImpl();
        final BootstrapConfig config = new BootstrapConfig();
        final ServerSecurity serverSecurity = createServerSecurity();
        config.security.put(0, serverSecurity);
        final ServerConfig server = createServerConfig();
        config.servers.put(0, server);

        store.addConfig(STORED_ENDPOINT_IDENTIFIER, config);

        // This has the result of saving
        store.deleteConfig("Doesn'tExist");

        final SecurityRegistryImpl registry = new SecurityRegistryImpl();
        final SecurityInfo info = createSecurityInfo();
        registry.add(info);
    }

    private static ServerConfig createServerConfig() {
        final ServerConfig server = new ServerConfig();
        server.shortId = 1;
        server.defaultMaxPeriod = Integer.MAX_VALUE;
        server.defaultMinPeriod = 0;
        server.disableTimeout = 0;
        return server;
    }

    private static SecurityInfo createSecurityInfo() {
        return SecurityInfo.newPreSharedKeyInfo(STORED_ENDPOINT_IDENTIFIER, PUBLIC_KEY_OR_IDENTITY,
                PRIVATE_KEY.getBytes());
    }

    @Test
    public void test() {
        final String key2 = "0788CB22F276A77A17981A94863533A9";
        final String key = "j9POrme0G0KeCZHjY33Vqeh7YoKQdu96R/S9Uw7BtxmSAmWA7pkRXymwejakewCa"
                + "uKir/aa9V7LYiNof4iV8ouucX3gyuX2LrY6ZqxwOzzCKOy9y+07AqLBGcDR3NOxW"
                + "SUZU28ExxekXWUF/o4D1T9hu4u+vBOx1wv7fjQ5T9ZOLSuN9gMRrJo2Kp6DqWajb"
                + "iDXk6PN4fTNbP/M5bkw3OxCRiOYsPYJFnVnZobaFVyOx1b7kYNaF+dfthpRSXYV7"
                + "JKuCWgNuQ4eivI6Lx20PGGd30gbYefezUfLT0IvuQ78hron4n9gdjne569OCLlpS"
                + "i+FXKxfO3MQZMoojEg0rvOp6THPb2zL6lh6bQqBM1/ay0/sWIZfLbCwo0hEuGQvM"
                + "Pb9m3yw0A0lhjD+S1UiOSEpSSE2XKAh6GWxRLpxxLrg=";

        System.err.println("Bytes: " + key.getBytes().length + "," + key2.getBytes().length);

    }

    private static ServerSecurity createServerSecurity() {
        final ServerSecurity serverSecurity = new ServerSecurity();
        serverSecurity.uri = "coaps://localhost:5684";
        serverSecurity.securityMode = SecurityMode.PSK;
        serverSecurity.publicKeyOrId = PUBLIC_KEY_OR_IDENTITY.getBytes();
        serverSecurity.serverPublicKeyOrId = PRIVATE_KEY.getBytes();
        serverSecurity.secretKey = "SECRET KEY".getBytes();
        serverSecurity.serverId = 1; // Let's just assume our LWM2M server will be 1 after bootstrap.
        serverSecurity.smsBindingKeyParam = "SBKP".getBytes();
        serverSecurity.smsBindingKeySecret = "SBKS".getBytes();
        // TODO As much as it makes no sense that Server SMS # is an INTEGER, having anything non-INTEGER here causes
        // the Leshan TLVdecoder to fail
        serverSecurity.serverSmsNumber = "2323";
        return serverSecurity;
    }

    // TODO this code was copied from BootstrapResource. Probably would be useful in a utility somewhere.
    private Tlv[] tlvEncode(final ServerSecurity value) {
        final Tlv[] resources = new Tlv[12];
        resources[0] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeString(value.uri), 0);
        resources[1] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeBoolean(value.bootstrapServer), 1);
        resources[2] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.securityMode.code), 2);
        resources[3] = new Tlv(TlvType.RESOURCE_VALUE, null, value.publicKeyOrId, 3);
        resources[4] = new Tlv(TlvType.RESOURCE_VALUE, null, value.serverPublicKeyOrId, 4);
        resources[5] = new Tlv(TlvType.RESOURCE_VALUE, null, value.secretKey, 5);
        resources[6] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.smsSecurityMode.code), 6);
        resources[7] = new Tlv(TlvType.RESOURCE_VALUE, null, value.smsBindingKeyParam, 7);
        resources[8] = new Tlv(TlvType.RESOURCE_VALUE, null, value.smsBindingKeySecret, 8);
        resources[9] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeString(value.serverSmsNumber), 9);
        resources[10] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.serverId), 10);
        resources[11] = new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.clientOldOffTime), 11);
        return resources;
    }

    private Tlv[] tlvEncode(final ServerConfig value) {
        final List<Tlv> resources = new ArrayList<Tlv>();
        resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.shortId), 0));
        resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.lifetime), 1));
        resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.defaultMinPeriod), 2));
        if (value.defaultMaxPeriod != null) {
            resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.defaultMaxPeriod), 3));
        }
        if (value.disableTimeout != null) {
            resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeInteger(value.disableTimeout), 5));
        }
        resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeBoolean(value.notifyIfDisabled), 6));
        resources.add(new Tlv(TlvType.RESOURCE_VALUE, null, TlvEncoder.encodeString(value.binding.name()), 7));

        return resources.toArray(new Tlv[] {});
    }
}
