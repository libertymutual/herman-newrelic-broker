/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.libertymutualgroup.herman.nr.broker.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.libertymutualgroup.herman.nr.broker.clients.NewRelicClient;
import com.libertymutualgroup.herman.nr.broker.domain.HermanBrokerStatus;
import com.libertymutualgroup.herman.nr.broker.domain.HermanBrokerUpdate;
import com.libertymutualgroup.herman.nr.broker.domain.NewRelicConfiguration;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.Application;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class AlertConfigurationService {

    @Autowired
    NewRelicClient newRelicClient;

    public List<HermanBrokerUpdate> configureAlerts(Application application, String policyName,
        NewRelicConfiguration configuration) {
        try {
            List<HermanBrokerUpdate> brokerUpdates = new ArrayList<>();
            if (configuration != null && configuration.getChannels() != null) {
                ObjectMapper objectMapper = new ObjectMapper();

                // Required property values
                ArrayNode channels = objectMapper.readValue(configuration.getChannels(), ArrayNode.class);

                // Optional property values
                ArrayNode applicationAlertsConditions = getApplicationAlertsConditions(configuration);
                ArrayNode pluginAlertsConditions = getPluginAlertsConditions(configuration);
                ArrayNode nrqlAlertsConditions = getNrqlAlertsConditions(configuration);
                ArrayNode infrastructureAlertsConditions = getInfrastructureAlertsConditions(configuration);
                ArrayNode synthetics = getSynthetics(configuration);

                Assert.isTrue(
                    applicationAlertsConditions != null
                        || pluginAlertsConditions != null
                        || nrqlAlertsConditions != null
                        || infrastructureAlertsConditions != null
                        || synthetics != null,
                    "There are no alerts conditions defined");

                // Delete existing policies and channels
                newRelicClient.deletePoliciesByName(policyName);
                newRelicClient.deleteChannelsByApplicationName(policyName);

                brokerUpdates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage("Previous alerts policy and channels deleted for application " + policyName));

                // Create new policy
                String policyId = newRelicClient.createPolicy(policyName);

                brokerUpdates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage("Alerts policy created: ID = " + policyId));

                // Create application alerts conditions
                if (applicationAlertsConditions != null && application != null) {
                    createApplicationAlertsConditions(application.getId(), objectMapper, applicationAlertsConditions,
                        policyId);

                    brokerUpdates.add(new HermanBrokerUpdate()
                        .withStatus(HermanBrokerStatus.PENDING)
                        .withMessage(String.format("%s alerts condition%s created", applicationAlertsConditions.size(),
                            applicationAlertsConditions.size() > 1 ? "s" : "")));
                }

                // Create plugin alerts conditions
                if (pluginAlertsConditions != null) {
                    pluginAlertsConditions.elements()
                        .forEachRemaining(condition -> newRelicClient.createPluginsCondition(policyId, condition));

                    brokerUpdates.add(new HermanBrokerUpdate()
                        .withStatus(HermanBrokerStatus.PENDING)
                        .withMessage(String
                            .format("%s plugin alerts condition%s created", pluginAlertsConditions.size(),
                                pluginAlertsConditions.size() > 1 ? "s" : "")));
                }

                // Create NRQL alerts conditions
                if (nrqlAlertsConditions != null) {
                    nrqlAlertsConditions.elements()
                        .forEachRemaining(condition -> newRelicClient.createNrqlAlertsConditions(policyId, condition));

                    brokerUpdates.add(new HermanBrokerUpdate()
                        .withStatus(HermanBrokerStatus.PENDING)
                        .withMessage(String.format("%s NRQL alerts condition%s created", nrqlAlertsConditions.size(),
                            nrqlAlertsConditions.size() > 1 ? "s" : "")));
                }

                if (infrastructureAlertsConditions != null) {
                    infrastructureAlertsConditions.elements()
                        .forEachRemaining(condition -> newRelicClient.createInfraAlertsConditions(policyId, condition));

                    brokerUpdates.add(new HermanBrokerUpdate()
                        .withStatus(HermanBrokerStatus.PENDING)
                        .withMessage(String.format("%s Infrastructure alerts condition%s created",
                            infrastructureAlertsConditions.size(),
                            infrastructureAlertsConditions.size() > 1 ? "s" : "")));
                }

                if (synthetics != null) {
                    synthetics.elements().forEachRemaining(
                        condition -> newRelicClient.createSynthetics(condition, policyName, policyId));

                    brokerUpdates.add(new HermanBrokerUpdate()
                        .withStatus(HermanBrokerStatus.PENDING)
                        .withMessage(String.format("%s Synthetics Monitor%s created", synthetics.size(),
                            synthetics.size() > 1 ? "s" : "")));
                }

                // Create alerts policy channels
                Set<String> channelIds = new HashSet<>();
                channels.elements().forEachRemaining(channel -> {
                    String channelName = channel.get("name").asText();
                    ((ObjectNode) channel).put("name", String.format("%s-%s", policyName, channelName));
                    channelIds.add(newRelicClient.createChannel(channel));
                });

                // Add channels to the policy
                newRelicClient.addChannelsToPolicy(channelIds, policyId);

                brokerUpdates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage(String.format("%s alerts policy channel%s created", channelIds.size(),
                        channelIds.size() > 1 ? "s" : "")));

            } else {
                brokerUpdates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage("Alert configuration is not defined in the Herman template file"));
            }

            return brokerUpdates;
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format("Error processing alert configuration request for policy %s", policyName),
                ex);
        }
    }

    private void createApplicationAlertsConditions(Integer applicationId, ObjectMapper objectMapper,
        ArrayNode conditions,
        String policyId) {
        conditions.elements().forEachRemaining(condition -> {
            ArrayNode entities = objectMapper.createArrayNode().add(applicationId.toString());
            ((ObjectNode) condition).set("entities", entities);
            newRelicClient.createApplicationAlertsConditions(policyId, condition);
        });
    }

    private ArrayNode getApplicationAlertsConditions(NewRelicConfiguration configuration) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ArrayNode applicationAlertsConditions = null;
            if (configuration.getConditions() != null) {
                applicationAlertsConditions = objectMapper.readValue(configuration.getConditions(), ArrayNode.class);
            }
            return applicationAlertsConditions;
        } catch (Exception ex) {
            throw new RuntimeException("Error getting application alerts conditions", ex);
        }
    }

    private ArrayNode getPluginAlertsConditions(NewRelicConfiguration configuration) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            ArrayNode pluginsConditions = null;
            if (configuration.getPluginConditions() != null) {
                pluginsConditions = objectMapper.readValue(configuration.getPluginConditions(), ArrayNode.class);
            }
            return pluginsConditions;

        } catch (Exception ex) {
            throw new RuntimeException("Error getting plugin alerts conditions", ex);
        }
    }

    private ArrayNode getNrqlAlertsConditions(NewRelicConfiguration configuration) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            ArrayNode getNrqlConditions = null;
            if (configuration.getNrqlConditions() != null) {
                getNrqlConditions = objectMapper.readValue(configuration.getNrqlConditions(), ArrayNode.class);
            }
            return getNrqlConditions;

        } catch (Exception ex) {
            throw new RuntimeException("Error getting NRQL alerts conditions", ex);
        }
    }

    private ArrayNode getInfrastructureAlertsConditions(NewRelicConfiguration configuration) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            ArrayNode getInfrastructureConditions = null;
            if (configuration.getInfrastructureConditions() != null) {
                getInfrastructureConditions = objectMapper
                    .readValue(configuration.getInfrastructureConditions(), ArrayNode.class);
            }
            return getInfrastructureConditions;
        } catch (Exception e) {
            throw new RuntimeException("Error getting Infrastructure alerts coinditions", e);
        }
    }

    private ArrayNode getSynthetics(NewRelicConfiguration configuration) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            ArrayNode getSynthetics = null;
            if (configuration.getSynthetics() != null) {
                getSynthetics = objectMapper.readValue(configuration.getSynthetics(), ArrayNode.class);
            }
            return getSynthetics;
        } catch (Exception e) {
            throw new RuntimeException("Error getting Synthetics", e);
        }
    }
}
