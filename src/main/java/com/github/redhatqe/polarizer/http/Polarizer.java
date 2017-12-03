package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.data.Serializer;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class Polarizer extends AbstractVerticle {

    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String UPLOAD_DIR = "/tmp";
    private Map<UUID, TestCaseConfig> tcMapperArgs = new HashMap<>();

    // Streams for processing data
    private BehaviorSubject<TestCaseConfig> testCaseHandler;
    private BehaviorSubject<XUnitConfig> xunitImportHandler;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> mapStream;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> jarStream;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<TestCaseConfig>>> testcaseStream;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitGenStream;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitImportStream;

    /**
     * The onNext handler for the mapStream
     *
     * @return handler to use for onNext for this.mapStream
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextMapStream() {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig tcfg = data.first;
            CompletionData<String> cd = data.second;
            tcfg.setMapping(cd.getResult());
            tcfg.completed.add("mapping");
        };
    }

    /**
     * The onNext handler for the jarStream
     *
     * @return handler to use for onNext for this.jarStream
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextJarStream() {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig tcfg = data.first;
            CompletionData<String> cd = data.second;
            tcfg.setPathToJar(cd.getResult());
            tcfg.completed.add("jar");
        };
    }

    /**
     * Creates handler for the testcaseStream
     *
     * @return handler to use for onNext for this.testcaseStream
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<TestCaseConfig>>>
    nextTCStream() {
        return (Tuple<TestCaseConfig, CompletionData<TestCaseConfig>> data) -> {
            TestCaseConfig tcfg = data.first;
            CompletionData<TestCaseConfig> cd = data.second;
            String mapping = tcfg.getMapping();
            String pathToJar = tcfg.getPathToJar();
            TestCaseConfig maybeCfg = cd.getResult();
            if (maybeCfg == null)
                this.testcaseStream.onError(new Error("Could not serialize testcase args"));
            else {
                tcfg = new TestCaseConfig(maybeCfg);
                tcfg.setMapping(mapping);
                tcfg.setPathToJar(pathToJar);
            }
        };
    }

    private Consumer<Tuple<XUnitConfig, CompletionData<XUnitConfig>>>
    nextXunitStream() {
        return (Tuple<XUnitConfig, CompletionData<XUnitConfig>> data) -> {
            XUnitConfig xargs = data.first;
            CompletionData<XUnitConfig> cd = data.second;
            String mapping = xargs.getMapping();
            XUnitConfig maybeCfg = cd.getResult();
            if (maybeCfg == null)
                this.xunitGenStream.onError(new Error("Could not serialize xunit args"));
            else {
                xargs = new XUnitConfig(maybeCfg);
                xargs.setMapping(mapping);
            }
        };
    }

    private Consumer<? super Throwable> errCompletion() {
        return err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        };
    }

    private Action streamComplete(String msg) {
        return () -> logger.info(String.format("Got completion event for a %s call", msg));
    }

    /**
     * Creates and sets up subscribers for the various streams
     */
    private void setupStreams() {
        this.mapStream = BehaviorSubject.create();
        this.jarStream = BehaviorSubject.create();
        this.testcaseStream = BehaviorSubject.create();
        this.testCaseHandler = BehaviorSubject.create();
        this.xunitGenStream = BehaviorSubject.create();
        this.xunitImportHandler = BehaviorSubject.create();

        this.mapStream.subscribe(this.nextMapStream(), this.errCompletion(), this.streamComplete("map stream"));
        this.jarStream.subscribe(this.nextJarStream(), this.errCompletion(), this.streamComplete("jar stream"));
        this.testcaseStream.subscribe(this.nextTCStream(), this.errCompletion(), this.streamComplete("tc stream"));
    }

    public void test(RoutingContext ctx) {
        // TODO: send msg on event bus to the APITestSuite verticle
        HttpServerRequest req = ctx.request();

        req.endHandler(resp -> {
            JsonObject jo = new JsonObject();
            jo.put("result", "TODO: make event bus message to APITestSuite");
            req.response().end(jo.encode());
        });
    }

    private <T> TestCaseConfig checkMapperArgs(CompletionData<T> data) {
        TestCaseConfig tcfg;
        if (tcMapperArgs.containsKey(data.getId()))
            tcfg = tcMapperArgs.get(data.getId());
        else {
            tcfg = new TestCaseConfig();
            tcMapperArgs.put(data.getId(), tcfg);
        }
        return tcfg;
    }

    private Handler<Void>
    uploadHandler( String msg
                 , String path
                 , String type
                 , UUID id
                 , BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> stream) {
        return (Void) -> {
            logger.info(msg);
            CompletionData<String> data = new CompletionData<>(type, path, id);
            TestCaseConfig tcfg = this.checkMapperArgs(data);
            Tuple<TestCaseConfig, CompletionData<String>> t = new Tuple<>(tcfg, data);
            stream.onNext(t);
        };
    }

    /**
     * Creates handler to be called when all form uploads have been completed
     *
     * This function will do the following if and when all the form uploads have completed
     * - Load the tcConfig file
     * - Load the mapping.json
     * - Call MainReflector on the downloaded jar
     *
     * @param req HttpServerRequest
     * @return handler for upload
     */
    private Handler<Void> testcaseMapHandler(HttpServerRequest req) {
        return v -> this.testCaseHandler.subscribe((TestCaseConfig cfg) -> {
            JsonObject jo = new JsonObject();
            // Get the TestCaseConfig object
            if (cfg.completed.size() != 3) {
                logger.error("Haven't finished getting all info yet");
            }

            try {
                jo = MainReflector.process(cfg);
                jo.put("result", "congratulations");
            } catch (IOException e) {
                e.printStackTrace();
                jo.put("result", "Error during reflection");
                jo.put("message", e.getMessage());
            }
            // TODO: We still need to return the new mapping.json to the user
            req.response().end(jo.encode());
        }, err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        }, () -> logger.info("Done processing REST call"));
    }

    /**
     * This is the handler for creating a mapping.json
     *
     * The body of the routing context will contain:
     *
     * @param ctx context supplied by server
     */
    private void testCaseMapper(RoutingContext ctx) {
        logger.info("In testcaseMapper");
        HttpServerRequest req = ctx.request();

        /*
         * Get the contents of the upload
         * FIXME: The tcConfig file and (probably) the mapping.json are small enough to stream into a memory for speed
         *
         * As each part is uploaded send off the completion data to a processing stream.  We use the UUID as a filter
         * so we can buffer up the three pieces together
         */
        UUID id = UUID.randomUUID();
        req.uploadHandler(upload -> {
            String fName = upload.name();
            switch(fName) {
                case "mapping":
                    String path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    String msg = "mapping.json now fully uploaded";
                    upload.endHandler(this.uploadHandler(msg, path, "mapping", id, this.mapStream));
                    break;
                case "jar":
                    String jarpath = FileHelper.makeTempFile("/tmp", "jarToCheck", ".jar", null).toString();
                    upload.streamToFileSystem(jarpath);
                    String jmsg = "jar file now fully uploaded";
                    upload.endHandler(this.uploadHandler(jmsg, jarpath, "jar", id, this.jarStream));
                    break;
                case "testcase":
                    String configPath;
                    File tPath = FileHelper.makeTempFile("/tmp", "testcase-args", ".json", null);
                    configPath = tPath.toString();
                    // FIXME: seems wasteful to stream to the filesystem then deserialize.  Just deserialize from buffer
                    upload.streamToFileSystem(configPath);
                    upload.endHandler(v -> {
                        logger.info("tcConfig file now fully uploaded");
                        TestCaseConfig tcArgs = null;
                        try {
                            tcArgs = Serializer.from(TestCaseConfig.class, tPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        CompletionData<TestCaseConfig> data = new CompletionData<>("tc", tcArgs, id);
                        Tuple<TestCaseConfig, CompletionData<TestCaseConfig>> t = new Tuple<>(tcArgs, data);
                        this.testcaseStream.onNext(t);
                    });
                    break;
                default:
                    logger.error("Unknown file attribute");
            }
        });
        // Once everything is uploaded reflect on the jar and return the new mapping.json
        req.endHandler(this.testcaseMapHandler(req));
    }

    /**
     * This method will generate an XML xunit file compatible with the XUnit Importer
     *
     * The body of the context will contain the following from a form upload:
     * - xunit: a regular xunit xml file
     * - args: a JSON dict supplying extra parameters
     * - mapping: a mapping.json file
     *
     * @param rc context passed by server
     */
    private void xunitGenerator(RoutingContext rc) {
        logger.info("In xunitGenerator");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        req.uploadHandler(upload -> {
            String fname = upload.name();
            switch(fname) {
                case "xunit":
                    break;
                case "xargs":
                    break;
                case "mapping":
                    String path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    String msg = "mapping.json now fully uploaded";
                    upload.endHandler(this.uploadHandler(msg, path, "mapping", id, this.mapStream));
                    break;
                default:
                    break;
            }
        });

        // TODO: make call to the importerRequest
    }

    /**
     *
     * @param rc context passed by server
     */
    private void testcaseImport(RoutingContext rc) {

    }

    /**
     *
     * @param rc context passed by server
     */
    public void xunitImport(RoutingContext rc) {

    }

    public void start() {
        VertxOptions opts = new VertxOptions();
        opts.setBlockedThreadCheckInterval(120000);
        this.vertx = Vertx.vertx(opts);
        this.setupStreams();

        //EventBus bus = vertx.eventBus();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 9090);
        server.requestHandler(req -> {
            req.setExpectMultipart(true);
            router.route("/testcase/mapper").method(HttpMethod.POST).handler(this::testCaseMapper);
            router.post("/xunit/generator").handler(this::xunitGenerator);
            router.post("/xunit/import").handler(this::xunitImport);
            router.post("/testcase/import").handler(this::testcaseImport);
            router.post("/test").handler(this::test);

            router.route().handler(BodyHandler.create()
                    .setBodyLimit(209715200L)                 // Max Jar size is 200MB
                    .setDeleteUploadedFilesOnEnd(false)       // FIXME: for testing only.  In Prod set to true
                    .setUploadsDirectory(UPLOAD_DIR));

            router.accept(req);
        })
        .rxListen(portNumber)
        .subscribe(succ -> logger.info("Server is now listening"),
                   err -> logger.info(String.format("Server could not be started %s", err.getMessage())));
    }
}
