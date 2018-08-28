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

import com.libertymutualgroup.herman.nr.broker.NewRelicBrokerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableRetry
public class NewRelicClientConfig {

    @Autowired
    NewRelicBrokerProperties properties;

    @Bean
    @Primary
    @Qualifier("nr")
    RestTemplate newRelicRestTemplate() {
        return new RestTemplateBuilder().rootUri("https://api.newrelic.com/v2").build();
    }

    @Bean
    @Qualifier("infra")
    RestTemplate newRelicInfrastructureRestTemplate() {
        return new RestTemplateBuilder().rootUri("https://infra-api.newrelic.com/v2/").build();
    }

    @Bean
    @Qualifier("synthetics")
    RestTemplate newRelicSyntheticsTemplate() {
        return new RestTemplateBuilder().rootUri("https://synthetics.newrelic.com/synthetics/api/v3").build();
    }

    @Bean
    HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", properties.getApiKey());
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
