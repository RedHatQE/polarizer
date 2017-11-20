package com.github.redhatqe.polarizer.importer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;

/**
 * Represents the JSON request for an XUnit Import
 */
public class XUnitImporterReq {
    @JsonProperty
    private String url;
    @JsonProperty
    private String user;
    @JsonProperty
    private String pw;
    @JsonProperty(value="xunit-path")
    private File xunitXml;
    @JsonProperty
    private String selector;
    @JsonProperty
    private String address;

    public XUnitImporterReq() {

    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPw() {
        return pw;
    }

    public void setPw(String pw) {
        this.pw = pw;
    }

    public File getXunitXml() {
        return xunitXml;
    }

    public void setXunitXml(File xunitXml) {
        this.xunitXml = xunitXml;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
