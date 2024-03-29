package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.*;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestDefinitionType implements TestDefinition {
    @JsonProperty(value="project-ids", required=true)
    private List<DefTypes.Project> projectIDs;     // An array (actually set) of projects this definition applies to
    @JsonProperty(value="testcase-ids", required=true)
    private List<String> testCaseIDs;
    @JsonProperty
    private String title;          // If you don't use the default, must specify the xmlDesc path
    @JsonProperty
    private String description;    // Must have description but may come from @Test
    @JsonProperty
    private String setup;
    @JsonProperty
    private String teardown;
    @JsonProperty(value="linked-work-items")
    private List<LinkedItemType> linkedWorkItems;
    @JsonProperty
    private DefTypes.Importance importance;
    @JsonProperty
    private DefTypes.PosNeg posneg;
    @JsonProperty
    private DefTypes.Level level;
    @JsonProperty
    private DefTypes.Automation automation;
    @JsonProperty
    private String script;                // path or name of automation script/method
    @JsonProperty
    private TestTypeType testtype;
    @JsonProperty
    private Boolean importReady;

    // FIXME: In the TestCase importer, teststeps is actually just a string which seems wrong
    @JsonProperty
    private List<TestStepType> teststeps;

    // Rarely used
    @JsonProperty
    private String assignee;
    @JsonProperty
    private String initialEstimate;
    @JsonProperty
    private String tags;
    @JsonProperty
    private String component;
    @JsonProperty
    private String subcomponent;
    @JsonProperty
    private String upstream;

    // These are not directly used by the importer
    @JsonProperty
    private String xmlDesc;
    @JsonProperty
    private Boolean update;

    public TestDefinitionType() {

    }

    public TestDefinitionType(TestDefinition td) {
        this.assignee = td.assignee();
        this.automation = td.automation();
        this.description = td.description();
        this.importance = td.importance();
        this.initialEstimate = td.initialEstimate();
        this.level = td.level();
        this.linkedWorkItems = Arrays.stream(td.linkedWorkItems())
                .map(LinkedItemType::new)
                .collect(Collectors.toList());
        this.posneg = td.posneg();
        this.projectIDs = Arrays.stream(td.projectID()).collect(Collectors.toList());
        this.script = td.script();
        this.setup = td.setup();
        this.tags = td.tags();
        this.teardown = td.teardown();
        this.testCaseIDs = Arrays.stream(td.testCaseID()).collect(Collectors.toList());
        this.teststeps = Arrays.stream(td.teststeps())
                .map(TestStepType::new).collect(Collectors.toList());
        this.testtype = new TestTypeType(td.testtype());
        this.title = td.title();
        this.update = td.update();
        this.upstream = td.upstream();
        this.xmlDesc = td.xmlDesc();
    }

    public List<DefTypes.Project> getProjectIDs() {
        return projectIDs;
    }

    public void setProjectIDs(List<DefTypes.Project> projectIDs) {
        this.projectIDs = projectIDs;
    }

    public List<String> getTestCaseIDs() {
        return testCaseIDs;
    }

    public void setTestCaseIDs(List<String> testCaseIDs) {
        this.testCaseIDs = testCaseIDs;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSetup() {
        return setup;
    }

    public void setSetup(String setup) {
        this.setup = setup;
    }

    public String getTeardown() {
        return teardown;
    }

    public void setTeardown(String teardown) {
        this.teardown = teardown;
    }

    public List<LinkedItemType> getLinkedWorkItems() {
        return linkedWorkItems;
    }

    public void setLinkedWorkItems(List<LinkedItemType> linkedWorkItems) {
        this.linkedWorkItems = linkedWorkItems;
    }

    public DefTypes.Importance getImportance() {
        return importance;
    }

    public void setImportance(DefTypes.Importance importance) {
        this.importance = importance;
    }

    public DefTypes.PosNeg getPosneg() {
        return posneg;
    }

    public void setPosneg(DefTypes.PosNeg posneg) {
        this.posneg = posneg;
    }

    public DefTypes.Level getLevel() {
        return level;
    }

    public void setLevel(DefTypes.Level level) {
        this.level = level;
    }

    public DefTypes.Automation getAutomation() {
        return automation;
    }

    public void setAutomation(DefTypes.Automation automation) {
        this.automation = automation;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public TestTypeType getTesttype() {
        return testtype;
    }

    public void setTesttype(TestTypeType testtype) {
        this.testtype = testtype;
    }

    public List<TestStepType> getTeststeps() {
        return teststeps;
    }

    public void setTeststeps(List<TestStepType> teststeps) {
        this.teststeps = teststeps;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getInitialEstimate() {
        return initialEstimate;
    }

    public void setInitialEstimate(String initialEstimate) {
        this.initialEstimate = initialEstimate;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getComponent() {
        return component;
    }

    public String component() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }

    public String subcomponent(){
	return subcomponent;
    };
    
    public String getSubcomponent() {
         return subcomponent;
    }

    public void setSubcomponent(String subcomponent) {
         this.subcomponent = subcomponent;
    }

    public String getUpstream() {
        return upstream;
    }

    public void setUpstream(String upstream) {
        this.upstream = upstream;
    }

    public String getXmlDesc() {
        return xmlDesc;
    }

    public void setXmlDesc(String xmlDesc) {
        this.xmlDesc = xmlDesc;
    }

    public Boolean getUpdate() {
        return update;
    }

    public void setUpdate(Boolean update) {
        this.update = update;
    }

    public Boolean getImportReady() {
        return importReady;
    }

    public void setImportReady(Boolean importReady) {
        this.importReady = importReady;
    }

    @Override
    public DefTypes.Project[] projectID() {
        return new DefTypes.Project[0];
    }

    @Override
    public String[] testCaseID() {
        return new String[0];
    }

    @Override
    public String title() {
        return this.title;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public String setup() {
        return this.setup;
    }

    @Override
    public String teardown() {
        return this.teardown;
    }

    @Override
    public LinkedItem[] linkedWorkItems() {
        return this.linkedWorkItems.toArray(new LinkedItem[this.linkedWorkItems.size()]);
    }

    @Override
    public DefTypes.Importance importance() {
        return this.importance;
    }

    @Override
    public DefTypes.PosNeg posneg() {
        return this.posneg;
    }

    @Override
    public DefTypes.Level level() {
        return this.level;
    }

    @Override
    public DefTypes.Automation automation() {
        return this.automation;
    }

    @Override
    public String script() {
        return this.script;
    }

    @Override
    public TestType testtype() {
        return this.testtype;
    }

    @Override
    public TestStep[] teststeps() {
        return this.teststeps.toArray(new TestStep[this.teststeps.size()]);
    }

    @Override
    public String assignee() {
        return this.assignee;
    }

    @Override
    public String initialEstimate() {
        return this.initialEstimate;
    }

    @Override
    public String tags() {
        return this.tags;
    }

    @Override
    public String upstream() {
        return this.upstream;
    }

    @Override
    public boolean importReady() {
        return false;
    }

    @Override
    public String xmlDesc() {
        return this.xmlDesc;
    }

    @Override
    public boolean update() {
        return this.update;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return TestDefinitionType.class;
    }
}
