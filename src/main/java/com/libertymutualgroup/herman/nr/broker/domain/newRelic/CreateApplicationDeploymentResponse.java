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
package com.libertymutualgroup.herman.nr.broker.domain.newRelic;

public class CreateApplicationDeploymentResponse {

    ApplicationDeployment deployment;

    public ApplicationDeployment getDeployment() {
        return deployment;
    }

    public void setDeployment(ApplicationDeployment deployment) {
        this.deployment = deployment;
    }

    public CreateApplicationDeploymentResponse withDeployment(
        final ApplicationDeployment deployment) {
        this.deployment = deployment;
        return this;
    }

    @Override
    public String toString() {
        return "CreateApplicationDeploymentResponse{" +
            "deployment=" + deployment +
            '}';
    }
}
