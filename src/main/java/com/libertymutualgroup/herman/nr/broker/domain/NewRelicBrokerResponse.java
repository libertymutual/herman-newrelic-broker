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
package com.libertymutualgroup.herman.nr.broker.domain;

import java.util.ArrayList;
import java.util.List;

public class NewRelicBrokerResponse {

    List<HermanBrokerUpdate> updates = new ArrayList<>();
    String applicationId;

    public List<HermanBrokerUpdate> getUpdates() {
        return updates;
    }

    public void setUpdates(List<HermanBrokerUpdate> updates) {
        this.updates = updates;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public NewRelicBrokerResponse withUpdates(
        final List<HermanBrokerUpdate> updates) {
        this.updates = updates;
        return this;
    }

    public NewRelicBrokerResponse withApplicationId(final String applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    @Override
    public String toString() {
        return "NewRelicBrokerResponse{" +
            "updates=" + updates +
            ", applicationId='" + applicationId + '\'' +
            '}';
    }
}
