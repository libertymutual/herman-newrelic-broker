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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class ApplicationDeployment {

    private String id;
    private String revision;
    private String changelog; //optional
    private String description; //optional
    private String user; //optional

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public ApplicationDeployment withId(final String id) {
        this.id = id;
        return this;
    }

    public ApplicationDeployment withRevision(final String revision) {
        this.revision = revision;
        return this;
    }

    public ApplicationDeployment withChangelog(final String changelog) {
        this.changelog = changelog;
        return this;
    }

    public ApplicationDeployment withDescription(final String description) {
        this.description = description;
        return this;
    }

    public ApplicationDeployment withUser(final String user) {
        this.user = user;
        return this;
    }

    @Override
    public String toString() {
        return "ApplicationDeployment{" +
            "id='" + id + '\'' +
            ", revision='" + revision + '\'' +
            ", changelog='" + changelog + '\'' +
            ", description='" + description + '\'' +
            ", user='" + user + '\'' +
            '}';
    }
}
