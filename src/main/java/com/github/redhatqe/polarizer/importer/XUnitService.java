package com.github.redhatqe.polarizer.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.ImporterRequest;
import com.github.redhatqe.polarizer.exceptions.*;
import com.github.redhatqe.polarizer.messagebus.*;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.reporter.IdParams;
import com.github.redhatqe.polarizer.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarizer.reporter.jaxb.JAXBHelper;
import com.github.redhatqe.polarizer.reporter.jaxb.JAXBReporter;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.reporter.importer.xunit.*;
import com.github.redhatqe.polarizer.reporter.importer.xunit.Properties;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.JsonHelper;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import java.util.function.Function;

import static com.github.redhatqe.polarizer.reporter.configuration.Serializer.from;


/**
 * Class that handles junit report generation for TestNG
 *
 * Use this class when running TestNG tests as -reporter XUnitService.  It can be
 * configured through the polarize-config.xml file.  A default configuration is contained in the resources folder, but
 * a global environment variable of XUNIT_IMPORTER_CONFIG can also be set.  If this env var exists and it points to a
 * file, this file will be loaded instead.
 */
public class XUnitService {
    private final static Logger logger = LogManager.getLogger(XUnitService.class);
    public static String configPath = System.getProperty("polarize.config");
    public File cfgFile = null;
    private XUnitConfig config;
    private static List<String> failedSuites = new ArrayList<>();

    public XUnitService(XUnitConfig cfg) {
        this.config = cfg;
    }

    public XUnitService() {
        this.getConfig(null);
    }

    public void setXUnitConfig(String path) throws IOException {
        if (path == null || path.equals(""))
            return;
        File cfgFile = new File(path);
        this.config = Serializer.fromYaml(XUnitConfig.class, cfgFile);
        logger.info("Set XUnitReporter config to " + path);
    }

    public XUnitConfig getConfig(String path) {
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


    /**
     * Creates an xunit file compatible with the Polarion xunit importer service
     *
     * @param cfg contains arguments needed to convert xunit to polarion compatible xunit
     * @param xunit a "standard" xunit xml file
     * @return a new File that is compatible
     */
    public static Optional<File> createPolarionXunit(XUnitConfig cfg, File xunit) {
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
        maybeSuites = XUnitService.getTestSuitesFromXML(xunit);

        if (!maybeSuites.isPresent())
            throw new XMLUnmarshallError("Could not unmarshall the xunit file");
        Testsuites suites = maybeSuites.get();
        suites.getTestsuite()
                .forEach(ts -> ts.getTestcase().forEach(tc -> {
                        // Add the properties here
                        String qual = String.format("%s.%s", tc.getClassname(), tc.getName());
                        IdParams param = fn.apply(qual);
                        Properties props = tc.getProperties();
                        Property prop = new Property();

                    })
                );

        return Optional.empty();
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
                                    XUnitService.failedSuites.add(suite);
                                }
                            }
                        });
                        result.setStatus(MessageResult.Status.FAILED);
                        result.setErrorDetails("TestSuites failed to be updated: " + String.join(",", suites));
                    }
                    else {
                        logger.error(root.get("message").asText());
                        result.setStatus(MessageResult.Status.EMPTY_MESSAGE);
                        result.setErrorDetails(root.get("message").toString());
                    }
                }
            } catch (NullPointerException npe) {
                String err = "Unknown format of message from bus";
                logger.error(err);
                result.setStatus(MessageResult.Status.NP_EXCEPTION);
                result.setErrorDetails(err);
            } catch (JsonProcessingException e) {
                String err = "Unable to deserialize JsonNode";
                logger.error(err);
                result.setStatus(MessageResult.Status.WRONG_MESSAGE_FORMAT);
                result.setErrorDetails(err);
                e.printStackTrace();
            }
            return result;
        };
    }

    private static Optional<com.github.redhatqe.polarizer.reporter.importer.testcase.Testcase> getTestcaseFromXML(File xmlDesc) {
        JAXBReporter jaxb = new JAXBReporter();
        Optional<com.github.redhatqe.polarizer.reporter.importer.testcase.Testcase> tc;
        tc = IJAXBHelper.unmarshaller(com.github.redhatqe.polarizer.reporter.importer.testcase.Testcase.class, xmlDesc,
                jaxb.getXSDFromResource(com.github.redhatqe.polarizer.reporter.importer.testcase.Testcase.class));
        if (!tc.isPresent())
            return Optional.empty();
        com.github.redhatqe.polarizer.reporter.importer.testcase.Testcase tcase = tc.get();
        return Optional.of(tcase);
    }

    private static Optional<com.github.redhatqe.polarizer.reporter.importer.xunit.Testsuites>
    getTestSuitesFromXML(File xmlDesc) {
        JAXBReporter jaxb = new JAXBReporter();
        Optional<com.github.redhatqe.polarizer.reporter.importer.xunit.Testsuites> ts;
        ts = IJAXBHelper.unmarshaller(com.github.redhatqe.polarizer.reporter.importer.xunit.Testsuites.class, xmlDesc,
                jaxb.getXSDFromResource(com.github.redhatqe.polarizer.reporter.importer.xunit.Testsuites.class));
        if (!ts.isPresent())
            return Optional.empty();
        com.github.redhatqe.polarizer.reporter.importer.xunit.Testsuites suites = ts.get();
        return Optional.of(suites);
    }

    private static String checkSelector(String selector) {
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
        return selector;
    }

    private static File getXunitFile(File xml, String xunitPath, String user, String pw) {
        if (xunitPath.startsWith("https")) {
            File tmpPath = FileHelper.makeTempFile("/tmp", "testng-polarion-", ".xml", "rw-rw----");
            Optional<File> body = ImporterRequest.get(xunitPath, user, pw, tmpPath.toString());
            if (body.isPresent())
                xml = body.get();
            else
                throw new ImportRequestError(String.format("Could not download %s", xml.toString()));
        }
        if (!xml.exists())
            throw new ImportRequestError(String.format("Could not find xunit file %s", xunitPath));
        else
            return xml;
    }

    /**
     * Makes a POST to the polarion /xunit/import
     *
     * @param args XUnitConfig object containing all the necessary information
     * @throws IOException
     */
    public static JsonObject
    request(XUnitConfig args) throws IOException {
        String url = args.getServers().get("polarion").getUrl() + args.getXunit().getEndpoint();
        String selector = String.format("%s='%s'", args.getXunit().getSelector().getName(),
                args.getXunit().getSelector().getValue());
        String user = args.getServers().get("polarion").getUser();
        String pw = args.getServers().get("polarion").getPassword();
        String xunitPath = args.getCurrentXUnit();

        selector = checkSelector(selector);

        // If the xunitPath starts with http, then download it
        File xml = getXunitFile(new File(xunitPath), xunitPath, user, pw);

        // TODO: Have a way to pass the broker data in if needed
        String defaultBrokerPath = ICIBus.getDefaultConfigPath();
        BrokerConfig brokerCfg = Serializer.fromYaml(BrokerConfig.class, new File(defaultBrokerPath));

        CIBusListener<DefaultResult> cbl = new CIBusListener<>(XUnitService.xunitMsgHandler(), brokerCfg);
        String address = String.format("Consumer.%s.%s", cbl.getClientID(), CIBusListener.TOPIC);
        Optional<MessageResult<DefaultResult>> maybeResult;

        maybeResult = ImporterRequest.sendImportByTap( cbl, url, user, pw, xml, selector, address);
        MessageResult<DefaultResult> n = maybeResult.orElseGet(() -> {
            DefaultResult res = new DefaultResult();
            String msg = "Error POST'ing to /xunit/import";
            res.setText(msg);
            MessageResult<DefaultResult> err = new MessageResult<>(res, null, MessageResult.Status.SEND_FAIL);
            err.setErrorDetails(msg);
            return err;
        });

        JsonObject jo = new JsonObject();
        jo.put("status", n.getStatus().toString());
        jo.put("result", n.info.getText());
        jo.put("errors", n.getErrorDetails());
        if (n.getStatus() == MessageResult.Status.SUCCESS)
            jo.put("new-xunit-path", xml.toString());
        else
            jo.put("new-xunit-path", "failed");


        return jo;
    }

    // FIXME:  make a real test
    public static void main(String[] args) throws IOException {
        XUnitService xs = new XUnitService();
        xs.setXUnitConfig(args[0]);
        XUnitConfig config = xs.getConfig(null);

        JsonObject jo = XUnitService.request(config);
    }
}
