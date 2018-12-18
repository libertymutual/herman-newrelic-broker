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
package com.libertymutualgroup.herman.nr.broker.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.Application;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.CreateApplicationDeploymentRequest;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.CreateApplicationDeploymentResponse;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.ListApplicationsResponse;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NewRelicClient {

    private static final Logger LOG = LoggerFactory.getLogger(NewRelicClient.class);

    @Autowired
    @Qualifier("nr")
    RestTemplate newRelicRestTemplate;

    @Autowired
    @Qualifier("infra")
    RestTemplate newRelicInfraRestTemplate;

    @Autowired
    @Qualifier("synthetics")
    RestTemplate newRelicSyntheticsTemplate;

    @Autowired
    HttpHeaders httpHeaders;

    @Autowired
    NewRelicSyntheticsClient newRelicSyntheticsClient;

    public Application getApplicationForAppName(String applicationName) {
        LOG.info("Finding New Relic applications with name {}", applicationName);

        ResponseEntity<ListApplicationsResponse> listApplicationsResponseEntity = newRelicRestTemplate.exchange(
            String.format("/applications.json?filter[name]=%s", applicationName),
            HttpMethod.GET,
            new HttpEntity<>(httpHeaders),
            ListApplicationsResponse.class);

        LOG.info("Found New Relic applications with name {}: {}", applicationName,
            listApplicationsResponseEntity.getBody());

        if (listApplicationsResponseEntity.getBody() != null && !listApplicationsResponseEntity.getBody()
            .getApplications().isEmpty()) {
            return listApplicationsResponseEntity.getBody().getApplications().stream()
                .filter(application -> applicationName.equals(application.getName()))
                .findAny()
                .orElse(null);
        } else {
            return null;
        }
    }

    public CreateApplicationDeploymentResponse createApplicationDeployment(Integer applicationId,
        CreateApplicationDeploymentRequest createApplicationDeploymentRequest) {
        return newRelicRestTemplate
            .exchange(
                String.format("/applications/%s/deployments.json", applicationId),
                HttpMethod.POST,
                new HttpEntity<>(createApplicationDeploymentRequest, httpHeaders),
                CreateApplicationDeploymentResponse.class)
            .getBody();
    }

    public void deletePoliciesByName(String policyName) {

        JsonNode alertsPoliciesResponse;
        try {
            alertsPoliciesResponse = newRelicRestTemplate
                .exchange(
                    String.format("/alerts_policies.json?filter[name]=%s", policyName),
                    HttpMethod.GET,
                    new HttpEntity<>(httpHeaders),
                    JsonNode.class)
                .getBody();
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Error getting alerts policies for name %s", policyName), ex);
        }

        ArrayNode policies = (ArrayNode) alertsPoliciesResponse.get("policies");
        if (policies.size() > 0) {
            JsonNode policy = policies.elements().next();
            String policyId = policy.get("id").asText();
            if (LOG.isInfoEnabled()) {
                LOG.info("Deleting policy with ID {}", policyId);
            }

            try {
                newRelicRestTemplate
                    .exchange(
                        String.format("/alerts_policies/%s.json", policyId),
                        HttpMethod.DELETE,
                        new HttpEntity<>(httpHeaders),
                        Void.class)
                    .getBody();
            } catch (Exception ex) {
                throw new RuntimeException(String.format("Error deleting alerts policy %s", policyId), ex);
            }
        } else {
            if (LOG.isInfoEnabled()) {
                LOG.info("No policies to delete");
            }
        }
    }

    public void deleteChannelsByApplicationName(String applicationName) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Deleting channels starting with name {}", applicationName);
        }

        Set<JsonNode> channels = getAllChannelsWithPrefix(applicationName);
        if (channels.isEmpty()) {
            if (LOG.isInfoEnabled()) {
                LOG.info(String.format("No channels found starting with name %s", applicationName));
            }
        } else {
            channels.stream().forEach(channel -> {
                String channelId = channel.get("id").asText();
                String channelName = channel.get("name").asText();
                LOG.info(String.format("Deleting channel with name %s and ID %s", channelName, channelId));
                newRelicRestTemplate.exchange(
                    String.format("/alerts_channels/%s.json", channelId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(httpHeaders),
                    Void.class);
            });
        }
    }

    private Set<JsonNode> getAllChannelsWithPrefix(String prefix) {
        Set<JsonNode> channels = new HashSet<>();

        String linkHeader;
        int page = 1;
        do {
            String url = String.format("/alerts_channels.json?page=%s", page);
            HttpEntity<JsonNode> responseEntity = newRelicRestTemplate
                .exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(httpHeaders),
                    JsonNode.class);

            ArrayNode channelArrayNode = (ArrayNode) responseEntity.getBody().get("channels");
            channelArrayNode.elements().forEachRemaining(channel -> {
                String channelName = channel.get("name").asText();
                if (channelName != null && channelName.startsWith(prefix)) {
                    channels.add(channel);
                }
            });

            page++;

            if (responseEntity.getHeaders().containsKey("Link")) {
                linkHeader = responseEntity.getHeaders().get("Link").get(0);
            } else {
                linkHeader = null;
            }
        } while (linkHeader != null && linkHeader.contains("next"));

        return channels;
    }

    public String createPolicy(String policyName) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Creating policy with name {}", policyName);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode policy = objectMapper.createObjectNode();
        policy.put("name", policyName);
        policy.put("incident_preference", "PER_POLICY");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("policy", policy);

        JsonNode result = newRelicRestTemplate
            .exchange(
                "/alerts_policies.json",
                HttpMethod.POST,
                new HttpEntity<JsonNode>(payload, httpHeaders),
                JsonNode.class)
            .getBody();

        return result.get("policy").get("id").asText();
    }

    public String getEntityIdForComponentName(String componentName) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Finding component entity IDs with name {}", componentName);
        }

        JsonNode response = newRelicRestTemplate
            .exchange(
                String.format("/components.json?filter[name]=%s", componentName),
                HttpMethod.GET,
                new HttpEntity<>(httpHeaders),
                JsonNode.class)
            .getBody();

        ArrayNode applications = (ArrayNode) response.get("components");
        Iterator<JsonNode> elementsIterator = applications.elements();
        while (elementsIterator.hasNext()) {
            JsonNode element = elementsIterator.next();
            if (componentName.equals(element.get("name").asText())) {
                String entityId = element.get("id").asText();
                if (LOG.isInfoEnabled()) {
                    LOG.info("Component found: {}", entityId);
                }
                return entityId;
            }
        }
        return null;
    }

    public void createApplicationAlertsConditions(String policyId, JsonNode condition) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Creating application alerts condition with name {} under policy ID {}",
                condition.get("name").asText(), policyId);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("condition", condition);

        try {
            newRelicRestTemplate
                .exchange(
                    String.format("/alerts_conditions/policies/%s.json", policyId),
                    HttpMethod.POST,
                    new HttpEntity<JsonNode>(payload, httpHeaders),
                    Void.class);
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Error creating application alerts condition for policy %s: %s",
                policyId,
                payload.toString()),
                ex);
        }
    }

    public void createPluginsCondition(String policyId, JsonNode pluginsCondition) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Creating plugins condition with name {} under policy ID {}",
                pluginsCondition.get("name").asText(),
                policyId);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("plugins_condition", pluginsCondition);

        try {
            newRelicRestTemplate
                .exchange(
                    String.format("/alerts_plugins_conditions/policies/%s.json", policyId),
                    HttpMethod.POST,
                    new HttpEntity<JsonNode>(payload, httpHeaders),
                    Void.class
                );
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Error creating plugins conditions for policy %s: %s",
                policyId,
                payload.toString()),
                ex);
        }

    }

    public String createChannel(JsonNode channel) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Creating channel with name {}", channel.get("name").asText());
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("channel", channel);

        JsonNode result = newRelicRestTemplate
            .exchange(
                "/alerts_channels.json",
                HttpMethod.POST,
                new HttpEntity<JsonNode>(payload, httpHeaders),
                JsonNode.class)
            .getBody();

        return result.get("channels").get(0).get("id").asText();
    }

    public void addChannelsToPolicy(Set<String> channelIds, String policyId) {
        LOG.info("Adding channels {} to policy with ID {}", channelIds, policyId);

        newRelicRestTemplate
            .exchange(
                String.format("/alerts_policy_channels.json?policy_id=%s&channel_ids=%s",
                    policyId,
                    String.join(",", channelIds)),
                HttpMethod.PUT,
                new HttpEntity<JsonNode>(httpHeaders),
                Void.class);
    }

    public void setApplicationApdex(Integer applicationId, String apdex) {
        // Build Application Update Body
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode settingsNode = mapper.createObjectNode();
        settingsNode.put("app_apdex_threshold", apdex);

        ObjectNode applicationNode = mapper.createObjectNode();
        applicationNode.set("settings", settingsNode);

        ObjectNode applicationUpdateNode = mapper.createObjectNode();
        applicationUpdateNode.set("application", applicationNode);

        if (LOG.isInfoEnabled()) {
            LOG.info("Updating application {}: {}", applicationId, applicationUpdateNode.toString());
        }

        newRelicRestTemplate
            .exchange(
                String.format("/applications/%s.json", applicationId),
                HttpMethod.PUT,
                new HttpEntity(applicationUpdateNode, httpHeaders),
                Void.class);
    }

    public void createNrqlAlertsConditions(String policyId, JsonNode condition) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("nrql_condition", condition);

        try {
            newRelicRestTemplate
                .exchange(
                    String.format("/alerts_nrql_conditions/policies/%s.json", policyId),
                    HttpMethod.POST,
                    new HttpEntity<JsonNode>(payload, httpHeaders),
                    Void.class
                );
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Error creating nrql conditions for policy %s: %s",
                policyId,
                payload.toString()),
                ex);
        }
    }

    public void createInfraAlertsConditions(String policyId, JsonNode condition) {
        LOG.info("Creating infrastructure condition under policy ID {}", policyId);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode data = ((ObjectNode) condition).put("policy_id", Integer.parseInt(policyId));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("data", data);

        try {
            newRelicInfraRestTemplate
                .exchange(
                    "/alerts/conditions",
                    HttpMethod.POST,
                    new HttpEntity<JsonNode>(payload, httpHeaders),
                    Void.class
                );
        } catch (Exception e) {
            throw new RuntimeException(String.format("Error creating Infrastructure conditions for policy %s: %s",
                policyId,
                payload),
                e);
        }
    }

    public void createSynthetics(JsonNode synthetics, String policyName, String polciyId) {
        deleteExistingSyntheticsMonitors(policyName);
        createSyntheticsMonitors(synthetics, policyName);
        createSyntheticsConditions(policyName, polciyId);
    }

    private void deleteExistingSyntheticsMonitors(String policyName) {
        newRelicSyntheticsClient.deleteExistingSyntheticsMonitors(policyName);
    }

    private void createSyntheticsMonitors(JsonNode synthetics, String policyName) {
        newRelicSyntheticsClient.createSyntheticsMonitors(synthetics, policyName);
    }

    private void createSyntheticsConditions(String policyName, String policyId) {
        newRelicSyntheticsClient.createSyntheticsConditions(policyName, policyId);
    }
}
