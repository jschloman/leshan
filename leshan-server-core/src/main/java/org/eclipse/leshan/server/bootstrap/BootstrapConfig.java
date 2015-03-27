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
package org.eclipse.leshan.server.bootstrap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.leshan.core.request.BindingMode;

/**
 * A client configuration to be pushed by a bootstrap operation
 */
public class BootstrapConfig implements Serializable {

    public Map<Integer, ServerConfig> servers = new HashMap<>();

    public Map<Integer, ServerSecurity> security = new HashMap<>();

    /** server configuration (object 1) */
    static public class ServerConfig implements Serializable {
        public int shortId;
        public int lifetime = 86400;
        public int defaultMinPeriod = 1;
        public Integer defaultMaxPeriod = null;
        public Integer disableTimeout = null;
        public boolean notifyIfDisabled = true;
        public BindingMode binding = BindingMode.U;

        @Override
        public String toString() {
            return String
                    .format("ServerConfig [shortId=%s, lifetime=%s, defaultMinPeriod=%s, defaultMaxPeriod=%s, disableTimeout=%s, notifyIfDisabled=%s, binding=%s]",
                            shortId, lifetime, defaultMinPeriod, defaultMaxPeriod, disableTimeout, notifyIfDisabled,
                            binding);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ServerConfig other = (ServerConfig) obj;
            return Objects.equals(this.shortId, other.shortId) && Objects.equals(this.lifetime, other.lifetime)
                    && Objects.equals(this.defaultMinPeriod, other.defaultMinPeriod)
                    && Objects.equals(this.defaultMaxPeriod, other.defaultMaxPeriod)
                    && Objects.equals(this.disableTimeout, other.disableTimeout)
                    && Objects.equals(this.notifyIfDisabled, other.notifyIfDisabled)
                    && Objects.equals(this.binding, other.binding);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shortId, lifetime, defaultMinPeriod, defaultMaxPeriod, disableTimeout,
                    notifyIfDisabled, binding);
        }
    }

    /** security configuration (object 0) */
    static public class ServerSecurity implements Serializable {
        public String uri;
        public boolean bootstrapServer = false;
        public SecurityMode securityMode;
        public byte[] publicKeyOrId = new byte[] {};
        public byte[] serverPublicKeyOrId = new byte[] {};
        public byte[] secretKey = new byte[] {};
        public SmsSecurityMode smsSecurityMode = SmsSecurityMode.NO_SEC;
        public byte[] smsBindingKeyParam = new byte[] {};
        public byte[] smsBindingKeySecret = new byte[] {};
        public String serverSmsNumber = ""; // spec says integer WTF?
        public Integer serverId;
        public int clientOldOffTime = 1;

        @Override
        public String toString() {
            return String
                    .format("ServerSecurity [uri=%s, bootstrapServer=%s, securityMode=%s, publicKeyOrId=%s, serverPublicKeyOrId=%s, secretKey=%s, smsSecurityMode=%s, smsBindingKeyParam=%s, smsBindingKeySecret=%s, serverSmsNumber=%s, serverId=%s, clientOldOffTime=%s]",
                            uri, bootstrapServer, securityMode, Arrays.toString(publicKeyOrId),
                            Arrays.toString(serverPublicKeyOrId), Arrays.toString(secretKey), smsSecurityMode,
                            Arrays.toString(smsBindingKeyParam), Arrays.toString(smsBindingKeySecret), serverSmsNumber,
                            serverId, clientOldOffTime);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ServerSecurity other = (ServerSecurity) obj;
            return Objects.equals(this.uri, other.uri) && Objects.equals(this.bootstrapServer, other.bootstrapServer)
                    && Objects.equals(this.securityMode, other.securityMode)
                    && Arrays.equals(this.publicKeyOrId, other.publicKeyOrId)
                    && Arrays.equals(this.serverPublicKeyOrId, other.serverPublicKeyOrId)
                    && Arrays.equals(this.secretKey, other.secretKey)
                    && Objects.equals(this.smsSecurityMode, other.smsSecurityMode)
                    && Arrays.equals(this.smsBindingKeyParam, other.smsBindingKeyParam)
                    && Arrays.equals(this.smsBindingKeySecret, other.smsBindingKeySecret)
                    && Objects.equals(this.serverSmsNumber, other.serverSmsNumber)
                    && Objects.equals(this.serverId, other.serverId)
                    && Objects.equals(this.clientOldOffTime, other.clientOldOffTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri, securityMode, publicKeyOrId, serverPublicKeyOrId, secretKey, smsSecurityMode,
                    smsBindingKeyParam, smsBindingKeySecret, serverSmsNumber, serverId, clientOldOffTime);
        }
    }

    @Override
    public String toString() {
        return String.format("BootstrapConfig [servers=%s, security=%s]", servers, security);
    }

}
