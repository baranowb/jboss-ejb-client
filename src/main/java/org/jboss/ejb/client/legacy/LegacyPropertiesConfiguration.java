/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.client.legacy;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.DeploymentNodeSelector;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.wildfly.common.function.ExceptionSupplier;
import org.xnio.OptionMap;

/**
 * Client configuration which is configured through {@link Properties}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class LegacyPropertiesConfiguration {

    public static void configure(final EJBClientContext.Builder builder) {
        final JBossEJBProperties properties = JBossEJBProperties.getCurrent();
        configure(builder, properties);
    }

    public static void configure(final EJBClientContext.Builder builder, final JBossEJBProperties properties) {
        
        if (properties != null) {
            Logs.MAIN.legacyEJBPropertiesEJBConfigurationInUse();

            final List<JBossEJBProperties.ConnectionConfiguration> connectionList = properties.getConnectionList();
            for (JBossEJBProperties.ConnectionConfiguration connectionConfiguration : connectionList) {
                final String host = connectionConfiguration.getHost();
                if (host == null) {
                    continue;
                }
                final int port = connectionConfiguration.getPort();
                if (port == -1) {
                    continue;
                }
                final OptionMap connectionOptions = connectionConfiguration.getConnectionOptions();
                final URI uri = CommonLegacyConfiguration.getUri(connectionConfiguration, connectionOptions);
                if (uri == null) {
                    continue;
                }
                final EJBClientConnection.Builder connectionBuilder = new EJBClientConnection.Builder();
                connectionBuilder.setDestination(uri);
                builder.addClientConnection(connectionBuilder.build());
            }

            final ExceptionSupplier<DeploymentNodeSelector, ReflectiveOperationException> deploymentNodeSelectorSupplier = properties.getDeploymentNodeSelectorSupplier();
            if (deploymentNodeSelectorSupplier != null) {
                final DeploymentNodeSelector deploymentNodeSelector;
                try {
                    deploymentNodeSelector = deploymentNodeSelectorSupplier.get();
                } catch (ReflectiveOperationException e) {
                    throw Logs.MAIN.cannotInstantiateDeploymentNodeSelector(properties.getDeploymentNodeSelectorClassName(), e);
                }
                builder.setDeploymentNodeSelector(deploymentNodeSelector);
            }

            Map<String, JBossEJBProperties.ClusterConfiguration> clusters = properties.getClusterConfigurations();
            if (clusters != null) {
                for (JBossEJBProperties.ClusterConfiguration cluster : clusters.values()) {
                    ExceptionSupplier<ClusterNodeSelector, ReflectiveOperationException> selectorSupplier = cluster.getClusterNodeSelectorSupplier();
                    if (selectorSupplier != null) {
                        try {
                            builder.setClusterNodeSelector(selectorSupplier.get());
                        } catch (ReflectiveOperationException e) {
                            throw Logs.MAIN.cannotInstantiateClustertNodeSelector(cluster.getClusterNodeSelectorClassName(), e);
                        }
                        // We only support one selector currently
                        break;
                    }
                }
            }

            if (properties.getInvocationTimeout() != -1L) {
                builder.setInvocationTimeout(properties.getInvocationTimeout());
            }
        }
    }

    private static final Set<String> LEGACY_KEYS;
    static {
        HashSet<String> tmp = new HashSet<>();
        tmp.add("remote.connection"); 
        tmp.add("remote.cluster"); 
        tmp.add("endpoint.name");
        tmp.add("deployment.node.selector"); 
        tmp.add("invocation.timeout");
        tmp.add("reconnect.tasks.timeout"); 
        tmp.add("deployment.node.selector");
        LEGACY_KEYS = Collections.unmodifiableSet(tmp);
    }
    /**
     * Check if properties contain legacy config options.
     * @param properties
     * @return
     */
    public static boolean containsLegacy(Map<String, ?> properties) {
        return properties.keySet().stream().anyMatch(key -> {
            return LEGACY_KEYS.stream().anyMatch(legacyKey -> {
                return key.contains(legacyKey);
            });
        });
    }
}
