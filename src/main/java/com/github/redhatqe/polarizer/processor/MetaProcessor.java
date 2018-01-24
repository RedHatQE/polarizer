package com.github.redhatqe.polarizer.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.metadata.DefTypes;
import com.github.redhatqe.polarize.metadata.LinkedItem;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.data.ProcessingInfo;
import com.github.redhatqe.polarizer.exceptions.*;
import com.github.redhatqe.polarizer.ImporterRequest;
import com.github.redhatqe.polarizer.reporter.configuration.TestCaseInfo;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseImportResult;
import com.github.redhatqe.polarizer.reporter.importer.testcase.*;
import com.github.redhatqe.polarizer.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarizer.reporter.jaxb.JAXBHelper;
import com.github.redhatqe.polarizer.messagebus.CIBusListener;
import com.github.redhatqe.polarizer.messagebus.MessageHandler;
import com.github.redhatqe.polarizer.messagebus.MessageResult;
import com.github.redhatqe.polarizer.reporter.IdParams;
import com.github.redhatqe.polarizer.utils.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.github.redhatqe.polarize.metadata.DefTypes.Custom.*;

public class MetaProcessor {
    public static Logger logger = LogManager.getLogger(MetaProcessor.class.getSimpleName());
    public static JAXBHelper jaxb = new JAXBHelper();

    /**
     * FIXME: I think this would have been better implemented as a composition
     *
     * Returns a lambda of a Consumer<DefType.Custom> that can be used to set custom fields
     *
     * @param supp Functional interface that takes a key, value args
     * @param def a TestDefinition object used to supply a value
     * @return
     */
    public static Consumer<DefTypes.Custom> customFieldsSetter(Consumer2<String, String> supp, TestDefinition def) {
        return key -> {
            switch (key) {
                case CASEAUTOMATION:
                    supp.accept(CASEAUTOMATION.stringify(), def.automation().stringify());
                    break;
                case CASEIMPORTANCE:
                    supp.accept(CASEIMPORTANCE.stringify(), def.importance().stringify());
                    break;
                case CASELEVEL:
                    supp.accept(CASELEVEL.stringify(), def.level().stringify());
                    break;
                case CASEPOSNEG:
                    supp.accept(CASEPOSNEG.stringify(), def.posneg().stringify());
                    break;
                case UPSTREAM:
                    supp.accept(UPSTREAM.stringify(), def.upstream());
                    break;
                case TAGS:
                    supp.accept(TAGS.stringify(), def.tags());
                    break;
                case SETUP:
                    supp.accept(SETUP.stringify(), def.setup());
                    break;
                case TEARDOWN:
                    supp.accept(TEARDOWN.stringify(), def.teardown());
                    break;
                case COMPONENT:
                    supp.accept(COMPONENT.stringify(), def.component());
                    break;
                case SUBCOMPONENT:
                    supp.accept(SUBCOMPONENT.stringify(), def.subcomponent());
                    break;
                case AUTOMATION_SCRIPT:
                    supp.accept(AUTOMATION_SCRIPT.stringify(), def.script());
                    break;
                case TESTTYPE:
                    supp.accept(TESTTYPE.stringify(), def.testtype().testtype().stringify());
                    break;
                case SUBTYPE1:
                    supp.accept(SUBTYPE1.stringify(), def.testtype().subtype1().toString());
                    break;
                case SUBTYPE2:
                    supp.accept(SUBTYPE2.stringify(), def.testtype().subtype2().toString());
                    break;
                default:
                    logger.warn(String.format("Unknown enum value: %s", key.toString()));
            }
        };
    }

    /**
     * Creates the TestSteps for the Testcase given values in the meta object
     *
     * @param meta Meta object containing parameter information
     * @param tc the Testcase object that will get TestSteps information added
     */
    private static void initTestSteps(Meta<TestDefinition> meta, Testcase tc) {
        TestSteps isteps = tc.getTestSteps();
        if (isteps == null) {
            isteps = new TestSteps();
        }
        List<TestStep> tsteps = isteps.getTestStep();

        // Takes a List<Parameter> and returns a TestStepColumn
        Transformer<List<Parameter>, TestStepColumn> parameterize = args -> {
            TestStepColumn col = new TestStepColumn();
            col.setId("step");
            args.forEach(a -> col.getParameter().add(a));
            return col;
        };

        // For automation needs, we will only ever have one TestStep (but perhaps with multiple columns).
        TestStep ts = new TestStep();
        List<TestStepColumn> cols = ts.getTestStepColumn();
        if (meta.params != null && meta.params.size() > 0) {
            TestStepColumn tcolumns = parameterize.transform(meta.params);
            cols.add(tcolumns);
        }
        else {
            TestStepColumn tsc = new TestStepColumn();
            tsc.setId("step");
            cols.add(tsc);
        }
        tsteps.add(ts);
        tc.setTestSteps(isteps);
    }

    private static void setLinkedWorkItems(Testcase tc, TestDefinition ann, String project) {
        LinkedItem[] li = ann.linkedWorkItems();
        LinkedWorkItems lwi = tc.getLinkedWorkItems();
        if (lwi == null)
            lwi = new LinkedWorkItems();
        List<LinkedWorkItem> links = lwi.getLinkedWorkItem();


        List<LinkedItem> litems = Arrays.stream(li)
                .filter((LinkedItem l) -> l.project().toString().equals(project))
                .collect(Collectors.toList());


        links.addAll(litems.stream()
                .map(wi -> {
                    LinkedWorkItem tcLwi = new LinkedWorkItem();
                    tcLwi.setWorkitemId(wi.workitemId());
                    tcLwi.setRoleId(wi.role().toString());
                    return tcLwi;
                })
                .collect(Collectors.toList()));
        if (links.size() > 0)
            tc.setLinkedWorkItems(lwi);
    }

    private static String getPolarionIDFromDef(TestDefinition def, String project) {
        int index = -1;
        DefTypes.Project[] projects = def.projectID();
        String[] ids = def.testCaseID();
        if (ids.length == 0)
            return "";

        for(int i = 0; i < projects.length; i++) {
            if (projects[i].toString().equals(project)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new PolarionMappingError("The meta.project value not found in TestDefintion.projectID()");
        }
        String pName;
        try {
            pName = ids[index];
        }
        catch (ArrayIndexOutOfBoundsException ex) {
            // This means that there were more elements in projectID than testCaseID.  Issue a warning, as this
            // could be a bug.  It can happen like this:
            // projectID={RHEL6, RedHatEnterpriseLinux7},
            // testCaseID="RHEL6-23478",
            pName = "";
        }
        return pName;
    }

    /**
     * Creates and initializes a Testcase object
     *
     * This function is mainly used to setup a Testcase object to be used for a Testcase importer request
     *
     * @param meta Meta object used to intialize Testcase information
     * @param methToDesc A map which looks up method name to description
     * @return Testcase object
     */
    public static Testcase
    initImporterTestcase(Meta<TestDefinition> meta, Map<String, String> methToDesc, TestCaseConfig cfg) {
        Testcase tc = new Testcase();
        TestDefinition def = meta.annotation;
        MetaProcessor.initTestSteps(meta, tc);
        CustomFields custom = tc.getCustomFields();
        if (custom == null)
            custom = new CustomFields();
        List<CustomField> fields = custom.getCustomField();
        DefTypes.Custom[] fieldKeys = { CASEAUTOMATION
                                      , CASEIMPORTANCE
                                      , CASELEVEL
                                      , CASEPOSNEG
                                      , UPSTREAM
                                      , TAGS
                                      , SETUP
                                      , TEARDOWN
                                      , AUTOMATION_SCRIPT
                                      , COMPONENT
                                      , SUBCOMPONENT
                                      , TESTTYPE
                                      , SUBTYPE1
                                      , SUBTYPE2};

        Consumer2<String, String> supp = (id, content) -> {
            CustomField field = new CustomField();
            if (!content.equals("")) {
                field.setId(id);
                field.setContent(content);
                fields.add(field);
            }
        };

        Consumer<DefTypes.Custom> transformer = MetaProcessor.customFieldsSetter(supp, def);
        for(DefTypes.Custom cust: fieldKeys) {
            transformer.accept(cust);
        }

        if (def.description().equals("") && methToDesc != null)
            tc.setDescription(methToDesc.get(meta.qualifiedName));
        else
            tc.setDescription(def.description());

        TestCaseInfo titleType = cfg.getTestcase();
        String t = "%s%s%s";
        String title;
        if (def.title().equals("")) {
            title = String.format(t, titleType.getPrefix(), meta.qualifiedName, titleType.getSuffix());
            tc.setTitle(title);
        }
        else {
            title = String.format(t, titleType.getPrefix(), def.title(), titleType.getSuffix());
            tc.setTitle(title);
        }

        MetaProcessor.setLinkedWorkItems(tc, def, meta.project);
        tc.setId(MetaProcessor.getPolarionIDFromDef(def, meta.project));
        tc.setCustomFields(custom);
        return tc;
    }

    /**
     * Generates the data in the mapping file as needed and determines if a testcase import request is needed
     *
     * @param meta
     * @param mapFile
     * @param tcToMeta
     * @param testCaseMap
     * @return
     */
    public static Tuple3<Testcase, Boolean, ProcessingInfo>
    processTC( Meta<TestDefinition> meta
             , Map<String, Map<String, IdParams>> mapFile
             , Map<Testcase, Meta<TestDefinition>> tcToMeta
             , Map<String, List<Testcase>> testCaseMap
             , File mapPath
             , Map<String, String> methToDesc
             , TestCaseConfig config) {
        Testcase tc = MetaProcessor.initImporterTestcase(meta, methToDesc, config);
        tcToMeta.put(tc, meta);

        int importType;
        Tuple3<Integer, Boolean, ProcessingInfo> res = processIdEntities(meta, mapFile, mapPath);
        importType = res.first;

        if (tc.getId().equals("") && !res.third.getMeta().polarionID.equals(""))
            tc.setId(res.third.getMeta().polarionID);

        // If the update bit and the none bit are 0 we don't do anything.  Otherwise, do an import request
        if (importType != 0) {
            String projId = meta.project;
            if (testCaseMap.containsKey(projId))
                testCaseMap.get(projId).add(tc);
            else {
                List<Testcase> tcs = new ArrayList<>();
                tcs.add(tc);
                testCaseMap.put(projId, tcs);
            }
        }

        return new Tuple3<>(tc, res.second, res.third);
    }

    public static Optional<String>
    getPolarionIDFromMapFile(String name, String project, Map<String, Map<String, IdParams>> mapFile) {
        Map<String, IdParams> pToID = mapFile.getOrDefault(name, null);
        if (pToID == null)
            return Optional.empty();
        IdParams ip = pToID.getOrDefault(project, null);
        if (ip == null)
            return Optional.empty();
        String id = ip.id;
        if (id.equals(""))
            return Optional.empty();
        else
            return Optional.of(id);
    }

    public static void
    setPolarionIDInMapFile(Meta<TestDefinition> meta, String id, Map<String, Map<String, IdParams>> mapFile) {
        String name = meta.qualifiedName;
        String project = meta.project;
        Map<String, IdParams> pToI;
        if (mapFile.containsKey(name)) {
            pToI = mapFile.get(name);
        }
        else {
            pToI = new LinkedHashMap<>();
        }

        IdParams ip; // = pToI.getOrDefault(project, null);
        List<String> params;
        if (meta.params != null) {
            params = meta.params.stream().map(Parameter::getName).collect(Collectors.toList());
        }
        else
            params = new ArrayList<>();
        ip = new IdParams(id, params);
        pToI.put(project, ip);
        mapFile.put(name, pToI);
    }

    private enum IDType {
        NONE, ANN, MAP, ALL;

        public static IDType fromNumber(int val) {
            switch (val) {
                case 0:
                    return NONE;
                case 1:
                    return MAP;
                case 2:
                    return ANN;
                case 3:
                    return ALL;
                default:
                    return null;
            }
        }
    }

    public enum Mismatch {
        METHOD_NOT_IN_MAPFILE(-1),
        METHOD_NOT_FOR_PROJECT(-2);

        int value;

        Mismatch(Integer val) {
            this.value = val;
        }

        public static int toInt(Mismatch m) {
            return m.value;
        }
    }

    /**
     * Determines the number of params from the Meta object and what's in the Mapping file.
     *
     * @param meta
     * @param mapFile
     * @return first element of tuple is num of args in method, second is num args in mapfile (or Mismatch value)
     */
    public static Tuple<Integer, Integer>
    paramCount(Meta<TestDefinition> meta, Map<String, Map<String, IdParams>> mapFile) {
        Tuple<Integer, Integer> params = new Tuple<>();
        params.first = meta.params.size();

        String qualName = meta.qualifiedName;
        Map<String, IdParams> methodToParams = mapFile.get(qualName);
        if (methodToParams == null) {
            params.second = Mismatch.toInt(Mismatch.METHOD_NOT_IN_MAPFILE);
            return params;
        }

        IdParams idparams = methodToParams.get(meta.project);
        if (idparams == null)
            params.second = Mismatch.toInt(Mismatch.METHOD_NOT_FOR_PROJECT);
        else
            params.second = idparams.getParameters().size();

        return params;
    }

    public static Map<String, Map<String, IdParams>> printSortedMappingFile(Map<String, Map<String, IdParams>> defs) {
        Map<String, Map<String, IdParams>> sorted = new TreeMap<>();
        for(Map.Entry<String, Map<String, IdParams>> me: defs.entrySet()) {
            String fnName = me.getKey();
            Map<String, IdParams> projMap = new TreeMap<>(me.getValue());
            sorted.put(fnName, projMap);
        }

        for(Map.Entry<String, Map<String, IdParams>> me: defs.entrySet()) {
            String key = me.getKey();
            Map<String, IdParams> val = me.getValue();
            String fmt = "{\n  %s : {\n    %s : {\n      id : %s,\n      params : %s\n    }\n}";
            for(Map.Entry<String, IdParams> e: val.entrySet()) {
                String project = e.getKey();
                IdParams param = e.getValue();
                String id = param.getId();
                String ps = param.getParameters().stream().reduce("", (acc, n) -> {
                    acc += n + ", ";
                    return acc;
                });
                if (ps.length() > 0)
                    ps = String.format("[ %s ]", ps.substring(0, ps.length() - 2));
                else
                    ps = "[ ]";
            }
        }
        return sorted;
    }

    /**
     * Creates the mapping JSON file given a Map of methodName -> Project -> IdParam
     *
     * @param mapPath path for where to write the JSON mapping
     * @param mpid a map of methodName to Project to IdParam object
     */
    public static void writeMapFile(File mapPath, Map<String, Map<String, IdParams>> mpid) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writer().withDefaultPrettyPrinter().writeValue(mapPath, printSortedMappingFile(mpid));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Given information from a Meta<TestDefinition> object and a Polarion ID for a TestCase, add to the mapFile
     *
     * Since the Meta object may not contain the polarionID, it is necessary to pass a non-null and valid ID.
     *
     * @param mapFile a map of function name to map of project -> parameter info
     * @param meta Meta of type TestDefinition used to get information for mapFile
     * @param id the string of the Polarion ID for t
     */
    public static void addToMapFile(Map<String, Map<String, IdParams>> mapFile,
                                    Meta<TestDefinition> meta,
                                    String id,
                                    File mapPath) {
        String msg = "Adding TestCase ID to the mapping file.  Editing map: %s -> {%s: %s}";
        logger.debug(String.format(msg, meta.qualifiedName, meta.project, id));
        Map<String, IdParams> projToId = mapFile.getOrDefault(meta.qualifiedName, null);
        if (projToId != null) {
            if (projToId.containsKey(meta.project)) {
                IdParams ip = projToId.get(meta.project);
                ip.id = id;
            }
            else {
                IdParams ip = new IdParams();
                ip.setId(id);
                ip.setParameters(meta.params.stream().map(Parameter::getName).collect(Collectors.toList()));
                projToId.put(meta.project, ip);
            }
        }
        else {
            // In this case, the key (method name) doesn't exist in map, so let's put it into this.mappingFile
            MetaProcessor.setPolarionIDInMapFile(meta, id, mapFile);
        }
        writeMapFile(mapPath, mapFile);
    }

    private static void checkParameterMismatch( Meta<TestDefinition> meta
            , Map<String
            , Map<String, IdParams>> mapFile
            , IDType idtype) {
        if (idtype == IDType.NONE)
            return;
        String qualName = meta.qualifiedName;
        Map<String, IdParams> methodToParams = mapFile.get(qualName);

        int paramSize = meta.params.size();
        if (methodToParams == null && paramSize == 0)
            return;
        if (methodToParams == null) {
            String err = String.format("Method %s has %d args, but doesnt exist in mapfile", qualName, paramSize);
            logger.warn("!!Need to edit mapping.json!! " + err);
            return;
        }

        IdParams params = methodToParams.get(meta.project);
        if (params == null)
            throw new MappingError(String.format("Could not find %s in map file for %s", qualName, meta.project));
        int mapFileArgSize = params.getParameters().size();
        if (mapFileArgSize != paramSize) {
            String msg = "For %s: number of params from method = %d, but number of params in Map File = %d";
            throw new MismatchError(String.format(msg, qualName, paramSize, mapFileArgSize));
        }
    }

    private static Boolean verifyEquality(String[] ids, String id) {
        return Arrays.stream(ids).allMatch(n -> n.equals(id));
    }

    private static Map<String, String> nonMatchingIds(Map<String, String> ids, String id) {
        return ids.entrySet().stream()
                .filter(i -> i.getValue().equals(id))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void writeBadFunctionText(List<String> badFunctions) {
        try {
            // FIXME: rotate the TestDefinitionProcess.errorsText
            final String warnText = "/tmp/polarize-warnings.txt";
            Path bf = Paths.get(warnText);
            StandardOpenOption opt = bf.toFile().exists() ? StandardOpenOption.APPEND : StandardOpenOption.CREATE;
            Files.write(bf, badFunctions, opt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * TODO: This function should return some kind of JsonObject
     *
     * This is the new version of processIdEntities.  In this new style, the testing is much simpler because there are
     * only two places to check now:
     *
     * - The Annotation
     * - The mapping.json
     *
     * Does what is needed based on whether the id exists in the annotation, xml or map file
     *
     * | annotation  | mapping  | Action(s)                                   | Name      |
     * |-------------|----------|---------------------------------------------|-----------|
     * | 0           | 0        | Make import request                         | NONE
     * | 0           | 1        | add to badFunction                          | ANN
     * | 1           | 0        | Edit the Mapping file, add to badFunction   | MAP
     * | 1           | 1        | Verify equality                             | ALL
     *
     * Note that this returns an integer value representing one of 4 possible states (really 3).  The update column
     * comes from the annotation bit
     *
     * | update?  | NONE? | Action
     * |----------|-------|-------------
     * | 0        | 0     | No update for this Testcase
     * | 0        | 1     | Make import request, and edit mapping
     * | 1        | 0     | Make import request, and edit mapping
     * | 1        | 1     | Make import request, and edit mapping verify
     *
     * @param meta A Meta of type TestDefinition which holds the annotation information for the method
     * @param mapFile A Map of qualified method -> project -> Polarion ID
     * @param mapPath Path to the mapping.json file (which is the persistent representation of mapFile)
     * @return An int (as binary) representing the 4 possible states for importing
     */
    private static Tuple3<Integer, Boolean, ProcessingInfo>
    processIdEntities(Meta<TestDefinition> meta,
                      Map<String, Map<String, IdParams>> mapFile,
                      File mapPath) {
        // TODO: Replace this by passing in a Subject
        List<String> badFuncs = new ArrayList<>();
        ProcessingInfo pi = pi = new ProcessingInfo("Unprocessed", meta);

        Optional<String> maybePolarionID = meta.getPolarionIDFromTestcase();
        Optional<String> maybeMapFileID =
                MetaProcessor.getPolarionIDFromMapFile(meta.qualifiedName, meta.project, mapFile);
        String annId = maybePolarionID.orElse("");
        String mapId = maybeMapFileID.orElse("");
        int importType = meta.annotation.update() ? 1 << 1 : 0;

        // w00t, bit tricks.  Thought I wouldn't need these again after my embedded days :)
        int idval = (annId.equals("") ? 0 : 1 << 1)  | (mapId.equals("") ? 0 : 1);
        IDType idtype = IDType.fromNumber(idval);
        if (idtype == null)
            throw new MappingError("Error in IDType.fromNumber()");

        // format: {0} method, {1} projectID, {2} ann or map {3} ID {4} ann or map
        String msg = "- %s for project %s: the ID=\"\" in the %s but is %s in the %s.";
        String qual = meta.qualifiedName;
        String project = meta.project;
        String pqual = meta.project + " -> " + qual;

        Boolean mapIsEdited = false;
        Tuple<Integer, Integer> count = paramCount(meta, mapFile);
        if (!count.first.equals(count.second)) {
            // If we couldn't find the method in the map, count.second == -1
            if (count.second != -1)
                mapIsEdited = true;
        }

        // TODO: Instead of throwing an error on mismatch, perhaps we should auto-correct based on precedence
        // FIXME: When query ability is added, can run a check
        String m = "ANN: Adding TestCase ID to the mapping file.  Editing map: %s -> {%s: %s}";
        switch (idtype) {
            case NONE:
                importType |= 0x01;
                pi.setMessage("NONE: No ID in annotation or mapping file");
                break;
            case ANN:
                logger.info(String.format(m, meta.qualifiedName, meta.project, annId));
                addToMapFile(mapFile, meta, annId, mapPath);
                badFuncs.add(String.format(msg, qual, project, "mapping", annId, "annotation"));
                pi.setMessage(m);
                break;
            case MAP:
                m = "MAP: ID exists in the mapping file, but not in the annotation";
                logger.info(String.format(m, meta.qualifiedName, meta.project, annId));
                badFuncs.add(String.format(msg, qual, project, "annotation", mapId, "mapping"));
                pi.setMessage(m);
                pi.getMeta().polarionID = mapId;
                break;
            case ALL:
                String[] all = {annId, mapId};
                if (!verifyEquality(all, annId)) {
                    Map<String, String> allIds = new HashMap<>();
                    allIds.put("annotation", annId);
                    allIds.put("map", mapId);
                    Map<String, String> unmatched = nonMatchingIds(allIds, annId);
                    unmatched.forEach((key, value) -> {
                        String err = "%s id = %s did not match %s in %s";
                        logger.error(String.format(err, key, value, annId, pqual));
                    });
                    if (!unmatched.isEmpty())
                        pi.setMessage(String.format("Mismatched IDs for %s in %s", qual, project));
                }
                pi.setMessage("ALL: ID is in both annotation and mapping");
                break;
            default:
                logger.error("Should not get here");
        }
        MetaProcessor.writeBadFunctionText(badFuncs);

        // If update bit is set, regenerate the XML file with the new data, however, check that xml file doesn't already
        // have the ID set.  If it does, add the ID to the tc.  Also, add to the mapping file
        if ((importType & 0b10) == 0b10) {
            String idtouse = annId;
            if (idtouse.equals("")) {
                idtouse = mapId;
            }
            if (!idtouse.equals("")) {
                MetaProcessor.setPolarionIDInMapFile(meta, idtouse, mapFile);
                //createMappingFile(mapPath, methToPD, mapFile);
            }
        }

        // At this point, make sure that the number of args in the method is how many we have in the mapping file.
        checkParameterMismatch(meta, mapFile, idtype);

        return new Tuple3<>(importType, mapIsEdited, pi);
    }

    /**
     * Initializes the Testcases object and returns an optional project ID
     *
     * @param selectorName name part of selector
     * @param selectorValue value part of selector (eg <name>='<value>')
     * @param projectID project ID of the Testcases object
     * @param testcaseXml File to where the Testcases object will be marshalled to
     * @param testMap a map of projectID to Testcase list
     * @param tests the Testcases object that will be initialized
     * @return an optional of the Testcases project
     */
    public static Optional<String>
    initTestcases(String selectorName,
                  String selectorValue,
                  String projectID,
                  File testcaseXml,
                  Map<String, List<Testcase>> testMap,
                  Testcases tests) {
        if (!testMap.containsKey(projectID)) {
            logger.error("ProjectType ID does not exist within Testcase Map");
            return Optional.empty();
        }
        if (testMap.get(projectID).isEmpty()) {
            logger.info(String.format("No testcases for %s to import", projectID));
            return Optional.empty();
        }
        tests.setProjectId(projectID);
        tests.getTestcase().addAll(testMap.get(projectID));

        ResponseProperties respProp = tests.getResponseProperties();
        if (respProp == null)
            respProp = new ResponseProperties();
        tests.setResponseProperties(respProp);
        List<ResponseProperty> props = respProp.getResponseProperty();
        if (props.stream().noneMatch(p -> p.getName().equals(selectorName) && p.getValue().equals(selectorValue))) {
            ResponseProperty rprop = new ResponseProperty();
            rprop.setName(selectorName);
            rprop.setValue(selectorValue);
            props.add(rprop);
        }

        JAXBHelper jaxb = new JAXBHelper();
        IJAXBHelper.marshaller(tests, testcaseXml, jaxb.getXSDFromResource(Testcases.class));
        return Optional.of(projectID);
    }


    /**
     * Finds a Testcase in testcases by matching name to the titles of the testcase
     *
     * @param name qualified name of method
     * @return the matching Testcase for the name
     */
    public static Testcase findTestcaseByName(String name, Testcases tests) {
        List<Testcase> tcs = tests.getTestcase().stream()
                .filter(tc -> {
                    String title = tc.getTitle();
                    return title.equals(name);
                })
                .collect(Collectors.toList());
        if (tcs.size() != 1) {
            logger.error("Found more than one matching qualified name in testcases");
            throw new SizeError();
        }
        return tcs.get(0);
    }

    public static JsonObject
    updateMappingFile(Map<String, Map<String, IdParams>> mapFile,
                      Map<String, Map<String, Meta<TestDefinition>>> methMap,
                      File mapPath,
                      JsonObject jo) {
        if (jo == null)
            jo = new JsonObject();
        JsonArray noIdsForProject = new JsonArray();
        JsonArray noIdsAnywhere = new JsonArray();
        JsonArray idInMNapButNotAnnotation = new JsonArray();
        JsonArray mismatchIds = new JsonArray();
        List<String> badFunctions = new ArrayList<>();
        methMap.forEach((fnName, projectToMeta) -> {
            projectToMeta.forEach((project, meta) -> {
                String id = meta.getPolarionIDFromTestcase().orElse("");
                int check = 0;
                String value = String.format("%s in %s", meta.qualifiedName, project);
                String mapid;

                // Check if the mapFile has the corresponding project of this function name
                if (!mapFile.containsKey(fnName) || !mapFile.get(fnName).containsKey(project)) {
                    if (id.equals(""))
                        noIdsForProject.add(value);
                    else
                        MetaProcessor.addToMapFile(mapFile, meta, id, mapPath);
                    return;
                } else {
                    mapid = mapFile.get(fnName).get(project).getId();
                    if (!mapid.equals(""))  // cases 7, 6, 4:  The mapId is not empty
                        check |= 1 << 2;
                    if (id.equals(""))
                        id = mapid;
                    else
                        check |= 1 << 1;    // cases 7, 6, 3, 2
                    if (mapid.equals(id))
                        check |= 1;         // cases 7, 5, 3, 1
                }

                switch (check) {
                    case 0:
                    case 1:
                        noIdsAnywhere.add(value);
                        break;
                    case 2:
                    case 3:
                        MetaProcessor.addToMapFile(mapFile, meta, id, mapPath);
                        break;
                    case 4:
                    case 5:
                        idInMNapButNotAnnotation.add(value);
                        break;
                    case 6:
                        String msg = "Map ID = %s, Ann ID = %s.  Replacing with Annotation value";
                        logger.warn(String.format(msg, mapid, id));
                        MetaProcessor.addToMapFile(mapFile, meta, id, mapPath);
                    case 7:
                        // nothing to do in this case, as the ID exists in the map file, and also in XML and annotation
                        break;
                    default:
                        logger.error("Unknown value for check");
                        break;
                }
            });
        });
        MetaProcessor.writeBadFunctionText(badFunctions);
        return jo;
    }

    /**
     * Creates a simple JSON file which maps a file system location to the Polarion ID
     *
     * Here's a rather complex example of a reduction.  Notice this uses the 3 arg version of reduce.
     * @return
     */
    public static Map<String, Map<String, IdParams>>
    createMappingFile(File mapPath,
                      Map<String, Map<String, Meta<TestDefinition>>> methToProjMeta,
                      Map<String, Map<String, IdParams>> mapFile) {
        logger.info("Generating mapping file based on all methods");
        HashMap<String, Map<String, IdParams>> collected = new HashMap<>();
        // Iterate through the map of qualifiedMethod -> ProjectID -> Meta<TestDefinition>
        Map<String, Map<String, IdParams>> mpid = methToProjMeta.entrySet().stream()
                .reduce(collected,
                        // Function that gets the inner map in methToProjMeta
                        (accum, entry) -> {
                            String methName = entry.getKey();
                            Map<String, Meta<TestDefinition>> methToDef = entry.getValue();
                            HashMap<String, IdParams> accumulator = new HashMap<>();
                            Map<String, IdParams> methToProject = methToDef.entrySet().stream()
                                    .reduce(accumulator,  // our "identity" value is the accumulator
                                            // Gets the map of String -> Meta<TestDefinition> inside methToProjMeta
                                            (acc, n) -> {
                                                String project = n.getKey();
                                                Meta<TestDefinition> m = n.getValue();
                                                if (mapFile.containsKey(methName)) {
                                                    Map<String, IdParams> pToI = mapFile.get(methName);
                                                    Boolean projectInMapping = pToI.containsKey(project);
                                                    if (projectInMapping) {
                                                        String idForProject = pToI.get(project).id;
                                                        Boolean idIsEmpty = idForProject.equals("");
                                                        if (!idIsEmpty) {
                                                            String msg = "Id for %s is in mapping file";
                                                            logger.debug(String.format(msg, idForProject));
                                                            m.polarionID = idForProject;
                                                        }
                                                        else
                                                            throw new MappingError("No ID for " + methName);
                                                    }
                                                }
                                                String id = m.polarionID;
                                                List<String> params = m.params.stream()
                                                        .map(Parameter::getName)
                                                        .collect(Collectors.toList());
                                                IdParams ip = new IdParams(id, params);

                                                acc.put(project, ip);
                                                return acc;
                                            },
                                            (a, next) -> {
                                                a.putAll(next);
                                                return a;
                                            });
                            accum.put(methName, methToProject);
                            return accum;
                        },
                        (partial, next) -> {
                            partial.putAll(next);
                            return partial;
                        });
        writeMapFile(mapPath, mpid);
        return mpid;
    }

    /**
     * TODO: This method should go somewhere else
     *
     * Returns a lambda usable as a handler for ImporterRequest.sendImportRequest
     *
     * This handler will take the ObjectNode (for example, decoded from a message on the message bus) gets the Polarion
     * ID from the ObjectNode, and edits the XML file with the Id.  It will also store
     *
     * @return lambda of a Consumer
     * @param methToProjectDef
     * @param projID
     * @param mapFile
     * @param mapPath
     * @param tt
     * @return
     */
    public static MessageHandler
    testcaseImportHandler( Map<String, Map<String, Meta<TestDefinition>>> methToProjectDef
                         , String projID
                         , Map<String, Map<String, IdParams>> mapFile
                         , File mapPath
                         , TestCaseInfo tt) {
        return (ObjectNode node) -> {
            MessageResult result = new MessageResult();
            if (node == null) {
                logger.warn("No message was received");
                result.setStatus(MessageResult.Status.NO_MESSAGE);
                return result;
            }
            JsonNode root = node.get("root");
            ObjectMapper mapper = new ObjectMapper();
            if (root.has("status")) {
                if (root.get("status").textValue().equals("failed")) {
                    result.setStatus(MessageResult.Status.FAILED);
                    result.errorDetails = "status was failed";
                    return result;
                }
            }

            JsonNode testcases = root.get("import-testcases");
            logger.info(testcases.asText());
            TestCaseImportResult tcResult = mapper.convertValue(root, TestCaseImportResult.class);
            result.setNode(node);
            String pf = tt.getPrefix();
            String sf = tt.getSuffix();
            testcases.forEach(n -> {
                // Take off the prefix and suffix from the testcase
                String name = n.get("name").textValue();
                name = name.replace(pf, "");
                name = name.replace(sf, "");

                if (!n.get("status").textValue().equals("failed")) {
                    String id = n.get("id").toString();
                    if (id.startsWith("\""))
                        id = id.substring(1);
                    if (id.endsWith("\""))
                        id = id.substring(0, id.length() -1);
                    logger.info(String.format("Testcase id for %s from message response = %s", name, id));
                    Meta<TestDefinition> meta = methToProjectDef.get(name).get(projID);
                    MetaProcessor.addToMapFile(mapFile, meta, id, mapPath);
                }
                else {
                    logger.error(String.format("Unable to add %s to mapping file", name));
                }
            });
            return result;
        };
    }

    public static class UpdateAnnotation {
        public String qualName;
        public String project;
        public Boolean update;

        public UpdateAnnotation(String q, String p, Boolean u) {
            this.qualName = q;
            this.project = p;
            this.update = u;
        }

        @Override
        public String toString() {
            return String.format("Method name: %s, Project: %s, Update: %b", qualName, project, update);
        }
    }

    /**
     * This method does several things.
     * - Check if there is a method annotated with @Test, but not @TestDefinition
     * - Checks if a method's TestDefinition.update = true
     * @return
     */
    public static Tuple<SortedSet<String>, List<UpdateAnnotation>>
    auditMethods(Set<String> atTestMethods, Map<String, Map<String, Meta<TestDefinition>>> atTD) {
        Set<String> atTDMethods = atTD.keySet();
        // The set of methods which are annotated with @Test but not with @TestDefinition
        Set<String> difference = atTestMethods.stream()
                .filter(e -> !atTDMethods.contains(e))
                .collect(Collectors.toSet());
        SortedSet<String> ordered = new TreeSet<>(difference);

        List<UpdateAnnotation> updateAnnotation = atTD.entrySet().stream()
                .flatMap(es -> {
                    String methname = es.getKey();
                    return es.getValue().entrySet().stream()
                            .map(es2 -> {
                                String project = es2.getKey();
                                Meta<TestDefinition> meta = es2.getValue();
                                return new UpdateAnnotation(methname, project, meta.annotation.update());
                            })
                            .collect(Collectors.toList())
                            .stream();
                })
                .filter(na -> na.update)
                .collect(Collectors.toList());
        return new Tuple<>(ordered, updateAnnotation);
    }

    public static JsonObject
    writeAuditJson(JsonObject jo, Tuple<SortedSet<String>, List<UpdateAnnotation>> audit) throws IOException {
        if (jo == null)
            jo = new JsonObject();
        Set<String> difference = audit.first;
        List<UpdateAnnotation> updates = audit.second;

        List<String> updateMsg = updates.stream()
                .map(UpdateAnnotation::toString)
                .collect(Collectors.toList());
        jo.put("needs-testdefinition", new JsonArray(new ArrayList<>(difference)));
        JsonArray ja = new JsonArray(updateMsg);
        jo.put("update-is-true", ja);
        return jo;
    }

    public static JsonObject writeAuditJson(JsonObject jo, String key, String value) {
        if (jo == null) {
            jo = new JsonObject();
        }
        jo.put(key, value);
        return jo;
    }

    /**
     * Sends a TestCase import request for each project
     *
     * @param testcaseMap
     * @param methToProjectDef
     * @param mapFile
     * @param mappingPath
     * @param config
     * @param brokerCfg
     * @return
     */
    public static List<Optional<MessageResult<ProcessingInfo>>>
    tcImportRequest(Map<String, List<Testcase>> testcaseMap,
                    Map<String, Map<String, Meta<TestDefinition>>> methToProjectDef,
                    Map<String, Map<String, IdParams>> mapFile,
                    File mappingPath,
                    TestCaseConfig config,
                    BrokerConfig brokerCfg) {
        List<Optional<MessageResult<ProcessingInfo>>> maybeNodes = new ArrayList<>();
        if (testcaseMap.isEmpty() || !config.getTestcase().getEnabled()) {
            // TODO:  Need to be able to return the JsonObject
            if (!testcaseMap.isEmpty()) {
                JsonObject jo = new JsonObject();
                String msg = "The TestCase Importer is disabled, but polarize detected that TestCase imports are " +
                        "required";
                jo.put("message", msg);
                testcaseMap.forEach((String project, List<Testcase> tcs) -> {
                    List<String> titles = tcs.stream().map(Testcase::getTitle).collect(Collectors.toList());
                    JsonArray jt = new JsonArray(titles);
                    jo.put("titles", jt);
                });
            }
            return maybeNodes;
        }

        String selName = config.getTestcase().getSelector().getName();
        String selVal = config.getTestcase().getSelector().getValue();
        String selector = String.format( "%s='%s'", selName, selVal);
        for(String project: testcaseMap.keySet()) {
            File testXml = FileHelper.makeTempFile("/tmp", "testcase-import", ".xml", null);
            Testcases tests = new Testcases();
            Optional<MessageResult<ProcessingInfo>> on;
            if (!MetaProcessor.initTestcases(selName, selVal, project, testXml, testcaseMap, tests)
                    .isPresent())
                maybeNodes.add(Optional.empty());
            else {
                MessageHandler hdlr;
                String projID = config.getProject();
                hdlr = MetaProcessor
                        .testcaseImportHandler( methToProjectDef
                                              , projID
                                              , mapFile
                                              , mappingPath
                                              , config.getTestcase());
                String url = config.getServers().get("polarion").getUrl() + config.getTestcase().getEndpoint();
                CIBusListener<ProcessingInfo> cbl = new CIBusListener<>(hdlr, brokerCfg);
                String address = String.format("Consumer.%s.%s", cbl.getClientID(), CIBusListener.TOPIC);
                on = ImporterRequest.sendImportByTap( cbl
                                                    , url
                                                    , config.getServers().get("polarion").getUser()
                                                    , config.getServers().get("polarion").getPassword()
                                                    , testXml
                                                    , selector
                                                    , address);
                maybeNodes.add(on);
            }
        }
        return maybeNodes;
    }
}