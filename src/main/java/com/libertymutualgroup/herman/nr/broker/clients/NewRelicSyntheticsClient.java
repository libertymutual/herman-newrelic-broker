package com.libertymutualgroup.herman.nr.broker.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NewRelicSyntheticsClient {

  @Autowired
  @Qualifier("nr")
  RestTemplate newRelicRestTemplate;

  @Autowired
  @Qualifier("synthetics")
  RestTemplate newRelicSyntheticsRestTemplate;

  @Autowired
  HttpHeaders httpHeaders;

  private static final Logger LOG = LoggerFactory.getLogger(NewRelicSyntheticsClient.class);

  public void createSyntheticsMonitors(JsonNode synthetics, String policyName) {
    LOG.info("Creating new Synthetics Monitor for {}", policyName);
    ObjectNode payload = ((ObjectNode)synthetics).put("name", policyName + "-synthetics");

    try {
      newRelicSyntheticsRestTemplate
          .exchange(
              "/monitors",
              HttpMethod.POST,
              new HttpEntity<>(payload, httpHeaders),
              Void.class
              );
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error Creating Synthetics Monitor: %s", payload), e);
    }
  }

  public void deleteExistingSyntheticsMonitors(String policyName) {
    LOG.info("Delete any existing Synthetics Monitors for {}", policyName);
    JsonNode syntheticsMonitorsResponse;
    try {
      syntheticsMonitorsResponse = newRelicSyntheticsRestTemplate
          .exchange(
            "/monitors",
            HttpMethod.GET,
            new HttpEntity<>(httpHeaders),
            JsonNode.class
          ).getBody();
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error deleting synthetics monitor: %s", policyName));
    }

    ArrayNode monitors = (ArrayNode) syntheticsMonitorsResponse.get("monitors");
    String id = getMonitorId(policyName);
    LOG.info("Deleting Synthetics Monitor with ID: {}", id);
    try {
      newRelicSyntheticsRestTemplate
          .exchange(
              String.format("/monitors/%s" ,id),
              HttpMethod.DELETE,
              new HttpEntity<>(httpHeaders),
              Void.class
          );
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error deleting synthetics monitor: %s", policyName));
    }
  }

  @Retryable(backoff = @Backoff(delay = 10000))
  public void createSyntheticsConditions(String policyName, String policyId) {
    LOG.info("Creating Synthetics Alert Conditions for {} under policy ID {}", policyName, policyId);
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode payload = objectMapper.createObjectNode();
    ObjectNode syntheticsCondition = objectMapper.createObjectNode();

    String monitorId = getMonitorId(policyName);

    syntheticsCondition.put("name", policyName + "-synthetics");
    syntheticsCondition.put("monitor_id", monitorId);
    syntheticsCondition.put("enabled", "true");
    payload.set("synthetics_condition", syntheticsCondition);

    try {
      newRelicRestTemplate
          .exchange(
            String.format("alerts_synthetics_conditions/policies/%s.json", policyId),
            HttpMethod.POST,
            new HttpEntity<>(payload, httpHeaders),
            Void.class
          );
    } catch (Exception e) {
      LOG.error("Error creating Synthetics alert condition for policy {} with payload {}", policyId, payload);
      throw new RuntimeException("Error Creating Synthetics Alerts Conditions", e);
    }
  }

  private String getMonitorId(String policyName) {
    JsonNode syntheticsMonitorsResponse;
    try {
      syntheticsMonitorsResponse = newRelicSyntheticsRestTemplate
          .exchange(
            "/monitors",
            HttpMethod.GET,
            new HttpEntity<>(httpHeaders),
            JsonNode.class
          ).getBody();
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error deleting synthetics monitor: %s", policyName));
    }

    ArrayNode monitors = (ArrayNode) syntheticsMonitorsResponse.get("monitors");
    if (monitors.size() > 0) {
      for (JsonNode monitor: monitors) {
        String monitorName = monitor.get("name").asText();
        if (monitorName.equalsIgnoreCase(policyName)) {
          return monitor.get("id").asText();
        }
      }
    }
    return null;
  }

}
