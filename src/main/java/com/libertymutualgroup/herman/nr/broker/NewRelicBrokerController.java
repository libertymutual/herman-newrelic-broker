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
package com.libertymutualgroup.herman.nr.broker;

import com.libertymutualgroup.herman.nr.broker.clients.NewRelicClient;
import com.libertymutualgroup.herman.nr.broker.domain.HermanBrokerStatus;
import com.libertymutualgroup.herman.nr.broker.domain.HermanBrokerUpdate;
import com.libertymutualgroup.herman.nr.broker.domain.NewRelicBrokerRequest;
import com.libertymutualgroup.herman.nr.broker.domain.NewRelicBrokerResponse;
import com.libertymutualgroup.herman.nr.broker.domain.newRelic.Application;
import com.libertymutualgroup.herman.nr.broker.services.AlertConfigurationService;
import com.libertymutualgroup.herman.nr.broker.services.ApplicationConfigurationService;
import com.libertymutualgroup.herman.nr.broker.services.ApplicationDeploymentService;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NewRelicBrokerController {

    private static final Logger LOG = LoggerFactory.getLogger(NewRelicBrokerController.class);

    @Autowired
    private NewRelicClient newRelicClient;

    @Autowired
    private ApplicationDeploymentService applicationDeploymentService;

    @Autowired
    private ApplicationConfigurationService applicationConfigurationService;

    @Autowired
    private AlertConfigurationService alertConfigurationService;

    public NewRelicBrokerResponse getResponse(NewRelicBrokerRequest newRelicBrokerRequest) {
        NewRelicBrokerResponse response = new NewRelicBrokerResponse();

        try {
            Application application = null;
            if (newRelicBrokerRequest.getNewRelicApplicationName() != null) {
                application = newRelicClient
                    .getApplicationForAppName(newRelicBrokerRequest.getNewRelicApplicationName());

                if (Optional.ofNullable(application).isPresent()) {
                    response.setApplicationId(application.getId().toString());
                    response.getUpdates().addAll(applicationDeploymentService.createApplicationDeployment(
                        application,
                        newRelicBrokerRequest.getDeployment()));
                    response.getUpdates().addAll(applicationConfigurationService.setApplicationApdex(
                        application,
                        newRelicBrokerRequest.getConfiguration()));
                } else {
                    response.getUpdates().add(new HermanBrokerUpdate()
                        .withStatus(HermanBrokerStatus.PENDING)
                        .withMessage(String.format("Application could not be found in New Relic: %s",
                            newRelicBrokerRequest.getNewRelicApplicationName())));
                }
            }

            response.getUpdates().addAll(alertConfigurationService.configureAlerts(
                application,
                newRelicBrokerRequest.getPolicyName(),
                newRelicBrokerRequest.getConfiguration()));

            response.getUpdates().add(new HermanBrokerUpdate()
                .withStatus(HermanBrokerStatus.OK)
                .withMessage("New Relic Broker processing has completed successfully"));

        } catch (Exception ex) {
            LOG.error("Error processing New Relic Broker request", ex);
            response.getUpdates().add(new HermanBrokerUpdate()
                .withStatus(HermanBrokerStatus.ERROR)
                .withMessage("New Relic Broker processing failed. See logs."));

        }

        return response;
    }
}