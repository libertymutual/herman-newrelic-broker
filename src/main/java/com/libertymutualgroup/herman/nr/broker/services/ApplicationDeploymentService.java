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
import com.libertymutualgroup.herman.nr.broker.clients.NewRelicClient;
import com.libertymutualgroup.herman.nr.broker.domain.HermanBrokerStatus;
import com.libertymutualgroup.herman.nr.broker.domain.HermanBrokerUpdate;
import com.libertymutualgroup.herman.nr.broker.domain.NewRelicApplicationDeploymentRequest;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.Application;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.ApplicationDeployment;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.CreateApplicationDeploymentRequest;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.CreateApplicationDeploymentResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApplicationDeploymentService {

    @Autowired
    NewRelicClient newRelicClient;

    public List<HermanBrokerUpdate> createApplicationDeployment(Application application, NewRelicApplicationDeploymentRequest deployment) {
        try {
            List<HermanBrokerUpdate> updates = new ArrayList<>();

            ObjectMapper objectMapper = new ObjectMapper();
            updates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage(
                        String.format("Application found: %s", objectMapper.writeValueAsString(application))));

            CreateApplicationDeploymentRequest createApplicationDeploymentRequest = new CreateApplicationDeploymentRequest()
                .withDeployment(new ApplicationDeployment()
                    .withRevision(deployment.getRevision())
                    .withDescription(deployment.getVersion())
                    .withUser(deployment.getUser()));

            updates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage(
                        String.format("Application deployment request: %s", objectMapper.writeValueAsString(createApplicationDeploymentRequest.getDeployment()))));

            CreateApplicationDeploymentResponse createApplicationDeploymentResponse = newRelicClient
                .createApplicationDeployment(application.getId(), createApplicationDeploymentRequest);

            updates.add(new HermanBrokerUpdate()
                    .withStatus(HermanBrokerStatus.PENDING)
                    .withMessage(String.format("Application deployment created: ID = %s",
                        createApplicationDeploymentResponse.getDeployment().getId())));

            return updates;
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format("Error processing application deployment request for application %s - Herman Request: %s", application, deployment),
                ex);
        }
    }
}
