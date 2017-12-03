package com.github.redhatqe.polarizer.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.ImporterRequest;
import com.github.redhatqe.polarizer.configuration.ServerInfo;
import com.github.redhatqe.polarizer.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.data.Serializer;
import com.github.redhatqe.polarizer.exceptions.*;
import com.github.redhatqe.polarizer.importer.xunit.*;
import com.github.redhatqe.polarizer.importer.xunit.Error;
import com.github.redhatqe.polarizer.jaxb.IJAXBHelper;
import com.github.redhatqe.polarizer.jaxb.JAXBHelper;
import com.github.redhatqe.polarizer.jaxb.JAXBReporter;
import com.github.redhatqe.polarizer.messagebus.*;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.reporter.IdParams;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.JsonHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.*;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.redhatqe.polarizer.data.Serializer.*;

/**
 * Class that handles junit report generation for TestNG
 *
 * Use this class when running TestNG tests as -reporter XUnitReporter.  It can be
 * configured through the polarize-config.xml file.  A default configuration is contained in the resources folder, but
 * a global environment variable of XUNIT_IMPORTER_CONFIG can also be set.  If this env var exists and it points to a
 * file, this file will be loaded instead.
 */
public class XUnitReporter implements IReporter {
    private final static Logger logger = LogManager.getLogger(XUnitReporter.class);
    public static String configPath = System.getProperty("polarize.config");
    public static File cfgFile = null;
    private static XUnitConfig config;
    private final static File defaultPropertyFile =
            new File(System.getProperty("user.home") + "/.polarize/reporter.properties");
    private static List<String> failedSuites = new ArrayList<>();

    public final static String templateId = "polarion-testrun-template-id";
    public final static String testrunId = "polarion-testrun-id";
    public final static String testrunTitle = "polarion-testrun-title";
    public final static String polarionCustom = "polarion-custom";
    public final static String polarionResponse = "polarion-response";
    private File bad = new File("/tmp/bad-tests.txt");

    public static void setXUnitConfig(String path) throws IOException {
        if (path == null || path.equals(""))
            return;
        File cfgFile = new File(path);
        XUnitReporter.config = Serializer.fromYaml(XUnitConfig.class, cfgFile);
        logger.info("Set XUnitReporter config to " + path);
    }

    public static XUnitConfig getConfig(String path) {
        if (path != null) {
            cfgFile = new File(path);
        }
        else if (configPath == null) {
            Path phome = Paths.get(System.getProperty("user.home"), ".polarizer", "polarizer-xunit.yml");
            cfgFile = new File(phome.toString());
        }
        else
            cfgFile = new File(configPath);

        if (!cfgFile.exists())
            throw new NoConfigFoundError(String.format("Could not config file %s", configPath));

        try {
            config = from(XUnitConfig.class, cfgFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConfigurationError("Could not serialize polarizer-xunit config file");
        }
        return config;
    }

    public static Properties getProperties() {
        Properties props = new Properties();
        Map<String, String> envs = System.getenv();
        if (envs.containsKey("XUNIT_IMPORTER_CONFIG")) {
            String path = envs.get("XUNIT_IMPORTER_CONFIG");
            File fpath = new File(path);
            if (fpath.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(fpath);
                    props.load(fis);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (XUnitReporter.defaultPropertyFile.exists()){
            try {
                FileInputStream fis = new FileInputStream(XUnitReporter.defaultPropertyFile);
                props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            InputStream is = XUnitReporter.class.getClassLoader().getResourceAsStream("reporter.properties");
            try {
                props.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }

    public void setTestSuiteResults(Testsuite ts, FullResult fr, ITestContext ctx) {
        if (fr == null)
            return;

        if (fr.total != fr.fails + fr.errors + fr.skips + fr.passes) {
            String e = "Total number of tests run != fails + errors + skips + passes\n";
            String v = "                       %d !=    %d +     %d +    %d +     %d\n";
            v = String.format(v, fr.total, fr.fails, fr.errors, fr.skips, fr.passes);
            System.err.println(e + v);
        }
        int numErrors = fr.errorsByMethod.size();


        // The iterations feature of Polarion means that we don't need to specify how many times a permutation of a
        // method + args passed/failed/skipped etc.  That's why we don't directly use the fr numbers.
        int numFails = ctx.getFailedTests().size();
        int numSkips = ctx.getSkippedTests().size();
        int numTotal = ctx.getAllTestMethods().length;
        if (numErrors > 0)
            numFails = numFails - numErrors;
        if (numFails <= 0)
            numFails = 0;
        ts.setErrors(Integer.toString(numErrors));
        ts.setFailures(Integer.toString(numFails));
        ts.setSkipped(Integer.toString(numSkips));
        ts.setTests(Integer.toString(numTotal));
    }

    /**
     * Creates an xunit file compatible with the Polarion xunit importer service
     *
     * @param cfg contains arguments needed to convert xunit to polarion compatible xunit
     * @param xunit a "standard" xunit xml file
     * @return a new File that is compatible
     */
    public Optional<File> createPolarionXunit(XUnitConfig cfg, File xunit) {
        Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(new File(cfg.getMapping()));
        String project = cfg.getProject();
        Function<String, IdParams> fn = (qual) -> {
            Map<String, IdParams> m = mapping.get(qual);
            if (m != null) {
                IdParams param =  m.get(project);
                if (param == null)
                    throw new MappingError(String.format("Could not find %s -> %s in mapping", qual, project));
                return param;
            }
            else
                throw new MappingError(String.format("Could not find %s in mapping", qual));
        };

        JAXBHelper jaxb = new JAXBHelper();
        Optional<Testsuites> maybeSuites;
        maybeSuites = XUnitReporter.getTestSuitesFromXML(xunit);

        if (!maybeSuites.isPresent())
            throw new XMLUnmarshallError("Could not unmarshall the xunit file");
        Testsuites suites = maybeSuites.get();
        suites.getTestsuite()
                .forEach(ts -> ts.getTestcase().forEach(tc -> {
                        // Add the properties here
                        String qual = String.format("%s.%s", tc.getClassname(), tc.getName());
                        IdParams param = fn.apply(qual);
                        com.github.redhatqe.polarizer.importer.xunit.Properties props = tc.getProperties();
                        Property prop = new Property();

                    })
                );

        return Optional.empty();
    }


    /**
     * Generates a modified xunit result that can be used for the XUnit Importer
     *
     * Example of a modified junit file:
     *
     * @param xmlSuites passed by TestNG
     * @param suites passed by TestNG
     * @param outputDirectory passed by TestNG.  configurable?
     */
    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        XUnitConfig config = XUnitReporter.getConfig(null);
        Testsuites tsuites = XUnitReporter.initTestSuiteInfo(config.getXunit().getSelector().getName());
        List<Testsuite> tsuite = tsuites.getTestsuite();

        if (this.bad.exists()) {
            try {
                Files.delete(this.bad.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Get information for each <testsuite>
        suites.forEach(suite -> {
            // suite here for the rhsm-qe tests should only be one occurrence
            Map<String, ISuiteResult> results = suite.getResults();
            Map<String, Tuple<FullResult, List<Testcase>>> full = XUnitReporter.getMethodInfo(suite, this.bad);
            List<Testsuite> collected = results.entrySet().stream()
                    .map(es -> {
                        // the results that we iterate through is each <test> element from the suite.xml.  From our
                        // perspective each <testsuite> is effectively the <test>, and in turn we model each <test>
                        // as a Class in java
                        Testsuite ts = new Testsuite();
                        List<Testcase> tests = ts.getTestcase();
                        if (tests == null)
                            tests = new ArrayList<>();

                        String key = es.getKey();
                        ISuiteResult result = es.getValue();
                        ITestContext ctx = result.getTestContext();
                        XmlTest xt = ctx.getCurrentXmlTest();
                        List<XmlClass> clses = xt.getClasses();
                        Set<String> clsSet = clses.stream()
                                .map(x -> {
                                    String sptCls = x.getSupportClass().toString();
                                    sptCls = sptCls.replace("class ", "");
                                    return sptCls;
                                })
                                .collect(Collectors.toSet());

                        ts.setName(key);
                        Date start = ctx.getStartDate();
                        Date end = ctx.getEndDate();
                        double duration = (end.getTime() - start.getTime()) / 1000.0;
                        ts.setTime(Double.toString(duration));

                        // While I suppose it's possible, we should have only one or zero possible results from the map
                        // so findFirst should return at most 1.  When will we have zero?
                        List<Tuple<FullResult, List<Testcase>>> frList = full.entrySet().stream()
                                .filter(e -> {
                                    String cls = e.getKey();
                                    return clsSet.contains(cls);
                                })
                                .map(Map.Entry::getValue)
                                .collect(Collectors.toList());
                        Optional<Tuple<FullResult, List<Testcase>>> maybeFR = frList.stream().findFirst();
                        Tuple<FullResult, List<Testcase>> tup = maybeFR.orElse(new Tuple<>());
                        FullResult fr = tup.first;
                        List<Testcase> tcs = tup.second;
                        if (tcs != null)
                            tests.addAll(tcs);

                        setTestSuiteResults(ts, fr, ctx);
                        if (fr == null) {
                            //System.out.println(String.format("Skipping test for %s", ctx.toString()));
                            return null;  // No FullResult due to empty frList.  Will be filtered out
                        }
                        else
                            return ts;
                    })
                    .filter(Objects::nonNull)  // filter out any suites without FullResult
                    .collect(Collectors.toList());

            tsuite.addAll(collected);
        });

        // Now that we've gone through the suites, let's marshall this into an XML file for the XUnit Importer
        FullResult suiteResults = getSuiteResults(tsuites);
        System.out.println(String.format("Error: %d, Failures: %d, Success: %d, Skips: %d", suiteResults.errors,
                suiteResults.fails, suiteResults.passes, suiteResults.skips));
        File reportPath = new File(outputDirectory + "/testng-polarion.xml");
        JAXBHelper jaxb = new JAXBHelper();
        IJAXBHelper.marshaller(tsuites, reportPath, jaxb.getXSDFromResource(Testsuites.class));
    }


    public static MessageHandler<DefaultResult> xunitMsgHandler() {
        return (ObjectNode node) -> {
            JsonNode root = node.get("root");
            MessageResult<DefaultResult> result = new MessageResult<>(node);
            result.info = new DefaultResult();

            try {
                Boolean passed = root.get("status").textValue().equals("passed");
                if (passed) {
                    logger.info("XUnit importer was successful");
                    logger.info(root.get("testrun-url").textValue());
                    result.info.setText(JsonHelper.nodeToString(root));
                    result.setStatus(MessageResult.Status.SUCCESS);
                }
                else {
                    // Figure out which one failed
                    if (root.has("import-results")) {
                        JsonNode results = root.get("import-results");
                        List<String> suites = new ArrayList<>();
                        results.elements().forEachRemaining(element -> {
                            if (element.has("status") && !element.get("status").textValue().equals("passed")) {
                                if (element.has("suite-name")) {
                                    String suite = element.get("suite-name").textValue();
                                    suites.add(suite);
                                    logger.info(suite + " failed to be updated");
                                    XUnitReporter.failedSuites.add(suite);
                                }
                            }
                        });
                        result.setStatus(MessageResult.Status.FAILED);
                        result.errorDetails = "TestSuites failed to be updated: " + String.join(",", suites);
                    }
                    else {
                        logger.error(root.get("message").asText());
                        result.setStatus(MessageResult.Status.EMPTY_MESSAGE);
                        result.errorDetails = root.get("message").toString();
                    }
                }
            } catch (NullPointerException npe) {
                String err = "Unknown format of message from bus";
                logger.error(err);
                result.setStatus(MessageResult.Status.NP_EXCEPTION);
                result.errorDetails = err;
            } catch (JsonProcessingException e) {
                String err = "Unable to deserialize JsonNode";
                logger.error(err);
                result.setStatus(MessageResult.Status.WRONG_MESSAGE_FORMAT);
                result.errorDetails = err;
                e.printStackTrace();
            }
            return result;
        };
    }

    /**
     * Sets the status for a Testcase object given values from ITestResult
     * 
     * @param result
     * @param tc
     */
    private static void getStatus(ITestResult result, Testcase tc, FullResult fr, String qual) {
        Throwable t = result.getThrowable();
        int status = result.getStatus();
        StringBuilder sb = new StringBuilder();
        fr.total++;
        switch(status) {
            // Unfortunately, TestNG doesn't distinguish between an assertion failure and an error.  The way to check
            // is if getThrowable() returns non-null
            case ITestResult.FAILURE:
                if (t != null && !(t instanceof java.lang.AssertionError)) {
                    fr.errors++;
                    if (!fr.errorsByMethod.contains(qual))
                        fr.errorsByMethod.add(qual);
                    Error err = new Error();
                    String maybe = t.getMessage();
                    if (maybe != null) {
                        String msg = t.getMessage().length() > 128 ? t.getMessage().substring(128) : t.getMessage();
                        err.setMessage(msg);
                    }
                    else
                        err.setMessage("java.lang.NullPointerException");
                    Arrays.stream(t.getStackTrace()).forEach(st -> sb.append(st.toString()).append("\n"));
                    err.setContent(sb.toString());
                    tc.getError().add(err);
                }
                else {
                    fr.fails++;
                    Failure fail = new Failure();
                    if (t != null)
                        fail.setContent(t.getMessage());
                    tc.getFailure().add(fail);
                }
                break;
            case ITestResult.SKIP:
                fr.skips++;
                tc.setSkipped("true");
                break;
            case ITestResult.SUCCESS:
                fr.passes++;
                tc.setStatus("success");
                break;
            default:
                if (t != null) {
                    Error err = new Error();
                    err.setMessage(t.getMessage().substring(128));
                    err.setContent(t.getMessage());
                    tc.getError().add(err);
                }
                break;
        }
    }

    public static boolean
    checkMethInMapping(Map<String, IdParams> inner, String qual, String project, File badMethods) {
        boolean in = true;
        if (inner == null || !inner.containsKey(project)) {
            String err = String.format("%s does not exist in mapping file for Project %s \n", qual, project);
            logger.error(err);
            try {
                FileWriter badf = new FileWriter(badMethods, true);
                BufferedWriter bw = new BufferedWriter(badf);
                bw.write(err);
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            in = false;
        }
        return in;
    }

    public FullResult getSuiteResults(Testsuites suites) {
        List<Testsuite> sList = suites.getTestsuite();
        return sList.stream()
                .reduce(new FullResult(),
                        (acc, s) -> {
                            acc.skips += Integer.parseInt(s.getSkipped());
                            acc.errors += Integer.parseInt(s.getErrors());
                            acc.fails += Integer.parseInt(s.getFailures());
                            acc.total += Integer.parseInt(s.getTests());
                            acc.passes = acc.passes + (acc.total - (acc.skips + acc.errors + acc.fails));
                            return acc;
                        },
                        FullResult::add);
    }

    /**
     * Gets information from each invoked method in the test suite
     *
     * @param suite suite that was run by TestNG
     * @return map of classname to a tuple of the FullResult and TestCase
     */
    private static Map<String, Tuple<FullResult, List<Testcase>>>
    getMethodInfo(ISuite suite, File badMethods) {
        List<IInvokedMethod> invoked = suite.getAllInvokedMethods();
        Map<String, Tuple<FullResult, List<Testcase>>> full = new HashMap<>();
        for(IInvokedMethod meth: invoked) {
            ITestNGMethod fn = meth.getTestMethod();
            if (!fn.isTest()) {
                continue;
            }

            ITestClass clz = fn.getTestClass();
            String methname = fn.getMethodName();
            String classname = clz.getName();

            // Load the mapping file
            String project = XUnitReporter.config.getProject();
            String path = XUnitReporter.config.getMapping();
            File fpath = new File(path);
            if (!fpath.exists()) {
                String err = String.format("Could not find mapping file %s", path);
                XUnitReporter.logger.error(err);
                throw new MappingError(err);
            }
            String qual = String.format("%s.%s", classname, methname);
            Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(fpath);
            Map<String, IdParams> inner = mapping.get(qual);

            if (!checkMethInMapping(inner, qual, project, badMethods))
                continue;

            FullResult fres;
            Testcase testcase = new Testcase();
            List<Testcase> tests;
            if (!full.containsKey(classname)) {
                fres = new FullResult();
                tests = new ArrayList<>();
                tests.add(testcase);
                full.put(classname, new Tuple<>(fres, tests));
            }
            else {
                Tuple<FullResult, List<Testcase>> tup = full.get(classname);
                fres = tup.first;
                tests = tup.second;
                tests.add(testcase);
            }
            ITestResult result = meth.getTestResult();
            Double millis = (result.getEndMillis() - result.getStartMillis()) / 1000.0;

            fres.classname = classname;
            testcase.setTime(millis.toString());
            testcase.setName(methname);
            testcase.setClassname(classname);
            XUnitReporter.getStatus(result, testcase, fres, qual);

            // Create the <properties> element, and all the child <property> sub-elements from the iteration data.
            // Gets the IdParams from the mapping.json file which has all the parameter information
            IdParams ip = inner.get(project);
            String id = ip.getId();
            List<String> args = ip.getParameters();
            Property polarionID = XUnitReporter.createProperty("polarion-testcase-id", id);
            com.github.redhatqe.polarizer.importer.xunit.Properties props =
                    getPropertiesFromMethod(result, args, polarionID);
            testcase.setProperties(props);
        }
        return full;
    }

    /**
     * Takes the parameter info from the mapping.json file for the TestCase ID, and generates the Properties for it
     *
     * @param result
     * @param args The list of args obtained from mapping.json for the matching polarionID
     * @param polarionID The matching Property of a Polarion ID for a TestCase
     * @return
     */
    public static com.github.redhatqe.polarizer.importer.xunit.Properties
    getPropertiesFromMethod(ITestResult result, List<String> args, Property polarionID) {
        com.github.redhatqe.polarizer.importer.xunit.Properties props =
                new com.github.redhatqe.polarizer.importer.xunit.Properties();
        List<Property> tcProps = props.getProperty();
        tcProps.add(polarionID);

        // Get all the iteration data
        Object[] params = result.getParameters();
        if (args.size() != params.length) {
            String name = String.format("testname: %s, methodname: %s", result.getTestName(), result.getMethod().getMethodName());
            XUnitReporter.logger.error(String.format("Length of parameters from %s not the same as from mapping file", name));
            String argList = args.stream().reduce("", (acc, n) -> acc + n + ",");
            logger.error(String.format("While checking args = %s", argList));
            throw new MappingError();
        }
        for(int x = 0; x < params.length; x++) {
            Property param = new Property();
            param.setName("polarion-parameter-" + args.get(x));
            String p;
            if (params[x] == null)
                p = "null";
            else
                p = params[x].toString();
            param.setValue(p);
            tcProps.add(param);
        }
        return props;
    }

    /**
     * Gets information from polarize-config to set as the elements in the <testsuites>
     *
     * @param responseName
     * @return
     */
    private static Testsuites initTestSuiteInfo(String responseName) {
        Testsuites tsuites = new Testsuites();
        com.github.redhatqe.polarizer.importer.xunit.Properties props =
                new com.github.redhatqe.polarizer.importer.xunit.Properties();
        List<Property> properties = props.getProperty();

        Property user = XUnitReporter.createProperty("polarion-user-id",
                config.getServers().get("polarion").getUser());
        properties.add(user);

        Property projectID = XUnitReporter.createProperty("polarion-project-id", config.getProject());
        properties.add(projectID);

        Map<String, Boolean> xprops = config.getXunit().getCustom().getTestSuite();
        Property testRunFinished = XUnitReporter.createProperty("polarion-set-testrun-finished",
                xprops.get("set-testrun-finished").toString());
        properties.add(testRunFinished);

        Property dryRun = XUnitReporter.createProperty("polarion-dry-run", xprops.get("dry-run").toString());
        properties.add(dryRun);

        Property includeSkipped = XUnitReporter.createProperty("polarion-include-skipped",
                xprops.get("include-skipped").toString());
        properties.add(includeSkipped);

        Configurator cfg = XUnitReporter.createConditionalProperty(XUnitReporter.polarionResponse, responseName,
                                                                   properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.polarionCustom, null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.testrunTitle, null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.testrunId, null, properties);
        cfg.set();
        cfg = XUnitReporter.createConditionalProperty(XUnitReporter.templateId, null, properties);
        cfg.set();

        tsuites.setProperties(props);
        return tsuites;
    }

    /**
     * Simple setter for a Property
     *
     * TODO: replace this with a lambda
     *
     * @param name key
     * @param value value of the key
     * @return Property with the given name and value
     */
    private static Property createProperty(String name, String value) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        return prop;
    }

    @FunctionalInterface
    interface Configurator {
        void set();
    }

    /**
     * Creates a Configurator functional interface useful to set properties for the XUnit importer
     *
     * @param name element name
     * @param value value for the element (might be attribute depending on XML element)
     * @param properties list of Property
     * @return The Configurator that can be used to set the given name and value
     */
    private static Configurator createConditionalProperty(String name, String value, List<Property> properties) {
        Configurator cfg;
        Property prop = new Property();
        prop.setName(name);

        switch(name) {
            case XUnitReporter.templateId:
                cfg = () -> {
                    String tempId = config.getXunit().getTestrun().getTemplateId();
                    if (tempId.equals(""))
                        return;
                    prop.setValue(tempId);
                    properties.add(prop);
                };
                break;
            case XUnitReporter.testrunTitle:
                cfg = () -> {
                    String trTitle = config.getXunit().getTestrun().getTitle();
                    if (trTitle.equals(""))
                        return;
                    prop.setValue(trTitle);
                    properties.add(prop);
                };
                break;
            case XUnitReporter.testrunId:
                cfg = () -> {
                    String trId = config.getXunit().getTestrun().getId();
                    if (trId.equals(""))
                        return;
                    prop.setValue(trId);
                    properties.add(prop);
                };
                break;
            case XUnitReporter.polarionResponse:
                cfg = () -> {
                    String selVal = config.getXunit().getSelector().getValue();
                    if (selVal.equals(""))
                        return;
                    prop.setName(XUnitReporter.polarionResponse + "-" + value);
                    prop.setValue(selVal);
                    properties.add(prop);
                };
                break;
            case XUnitReporter.polarionCustom:
                cfg = () -> {
                    Map<String, String> customFields = config.getXunit().getCustom().getProperties();
                    if (customFields.isEmpty())
                        return;
                    customFields.entrySet().forEach(entry -> {
                        String key = XUnitReporter.polarionCustom + "-" + entry.getKey();
                        String val = entry.getValue();
                        if (!val.equals("")) {
                            Property p = new Property();
                            p.setName(key);
                            p.setValue(val);
                            properties.add(p);
                        }
                    });
                };
                break;
            default:
                cfg = null;
        }
        return cfg;
    }

    private static Optional<com.github.redhatqe.polarizer.importer.testcase.Testcase> getTestcaseFromXML(File xmlDesc) {
        JAXBReporter jaxb = new JAXBReporter();
        Optional<com.github.redhatqe.polarizer.importer.testcase.Testcase> tc;
        tc = IJAXBHelper.unmarshaller(com.github.redhatqe.polarizer.importer.testcase.Testcase.class, xmlDesc,
                jaxb.getXSDFromResource(com.github.redhatqe.polarizer.importer.testcase.Testcase.class));
        if (!tc.isPresent())
            return Optional.empty();
        com.github.redhatqe.polarizer.importer.testcase.Testcase tcase = tc.get();
        return Optional.of(tcase);
    }

    private static Optional<com.github.redhatqe.polarizer.importer.xunit.Testsuites>
    getTestSuitesFromXML(File xmlDesc) {
        JAXBReporter jaxb = new JAXBReporter();
        Optional<com.github.redhatqe.polarizer.importer.xunit.Testsuites> ts;
        ts = IJAXBHelper.unmarshaller(com.github.redhatqe.polarizer.importer.xunit.Testsuites.class, xmlDesc,
                jaxb.getXSDFromResource(com.github.redhatqe.polarizer.importer.xunit.Testsuites.class));
        if (!ts.isPresent())
            return Optional.empty();
        com.github.redhatqe.polarizer.importer.xunit.Testsuites suites = ts.get();
        return Optional.of(suites);
    }

    /**
     *
     * @param user
     * @param pw
     * @param xunitPath
     * @param selector
     * @throws IOException
     */
    public static JsonObject
    request( String xunitPath
           , String user
           , String pw
           , String selector) throws IOException {
        String url = XUnitReporter.config.getServers().get("polarion").getUrl();
        url += XUnitReporter.config.getXunit().getEndpoint();
        if (selector == null) {
            selector = String.format("%s='%s'", XUnitReporter.config.getXunit().getSelector().getName(),
                    XUnitReporter.config.getXunit().getSelector().getValue());
        }

        if (user == null)
            user = config.getServers().get("polarion").getUser();
        if (pw == null)
            pw = config.getServers().get("polarion").getPassword();


        // Make sure selector is in proper format
        if (!selector.contains("'")) {
            String[] tokens = selector.split("=");
            if (tokens.length != 2)
                throw new InvalidArgument("--selector must be in form of name=val");
            String name = tokens[0];
            String val = tokens[1];
            selector = String.format("%s='%s'", name, val);
            logger.info("Modified selector to " + selector);
        }

        // If the xunitPath starts with http, then download it
        File xml = new File(xunitPath);
        if (xunitPath.startsWith("https")) {
            Optional<File> body = ImporterRequest.get(xunitPath, user, pw, "/tmp/testng-polarion.xml");
            if (body.isPresent())
                xml = body.get();
            else
                throw new ImportRequestError(String.format("Could not download %s", xml.toString()));
        }
        if (!xml.exists())
            throw new ImportRequestError(String.format("Could not find xunit file %s", xunitPath));

        String defaultBrokerPath = ICIBus.getDefaultConfigPath();
        BrokerConfig brokerCfg = Serializer.fromYaml(BrokerConfig.class, new File(defaultBrokerPath));

        CIBusListener<DefaultResult> cbl = new CIBusListener<>(XUnitReporter.xunitMsgHandler(), brokerCfg);
        String address = String.format("Consumer.%s.%s", cbl.getClientID(), CIBusListener.TOPIC);
        Optional<MessageResult<DefaultResult>> maybeResult;
        maybeResult = ImporterRequest.sendImportByTap( cbl
                                                     , url
                                                     , user
                                                     , pw
                                                     , xml
                                                     , selector
                                                     , address);
        MessageResult<DefaultResult> n = maybeResult.orElseThrow(() -> new MessageError("Did not get a response message from CI Bus"));
        JsonObject jo = new JsonObject();
        if (n.getStatus() != MessageResult.Status.SUCCESS) {
            jo.put("status", n.getStatus().toString());
            jo.put("result", n.info.getText());
            jo.put("errors", n.errorDetails);
        }

        return jo;
    }

    // FIXME:  make a real test
    public static void main(String[] args) throws IOException {
        XUnitConfig config = XUnitReporter.getConfig(null);
        ServerInfo polarion = config.getServers().get("polarion");
        String user = polarion.getUser();
        String pw = polarion.getPassword();
        String xunitPath = args[0];

        JsonObject jo = XUnitReporter.request(xunitPath, user, pw, null);
    }
}
