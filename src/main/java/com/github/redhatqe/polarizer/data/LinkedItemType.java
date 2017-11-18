package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.DefTypes;
import com.github.redhatqe.polarize.metadata.LinkedItem;

public class LinkedItemType {
    @JsonProperty(value="workitem-id")
    private String workitemId;
    @JsonProperty
    private Boolean suspect;
    @JsonProperty
    private DefTypes.Role role;
    @JsonProperty
    private DefTypes.Project project;
    @JsonProperty
    private String revision;

    public LinkedItemType() {

    }

    public LinkedItemType(LinkedItem li) {
        this.project = li.project();
        this.workitemId = li.workitemId();
        this.revision = li.revision();
        this.role = li.role();
        this.suspect = li.suspect();
    }

    public String getWorkitemId() {
        return workitemId;
    }

    public void setWorkitemId(String workitemId) {
        this.workitemId = workitemId;
    }

    public Boolean getSuspect() {
        return suspect;
    }

    public void setSuspect(Boolean suspect) {
        this.suspect = suspect;
    }

    public DefTypes.Role getRole() {
        return role;
    }

    public void setRole(DefTypes.Role role) {
        this.role = role;
    }

    public DefTypes.Project getProject() {
        return project;
    }

    public void setProject(DefTypes.Project project) {
        this.project = project;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

}
