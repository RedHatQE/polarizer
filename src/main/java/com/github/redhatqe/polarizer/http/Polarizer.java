package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.configuration.Serializer;
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
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerFileUpload;
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
    private Map<UUID, XUnitConfig> xunitMapperArgs = new HashMap<>();

    // Streams for processing data
    private BehaviorSubject<TestCaseConfig> testCaseHandler;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> tcMapperMap$;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> tcMapperJar$;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<TestCaseConfig>>> tcMapperTC$;

    private BehaviorSubject<XUnitConfig> xunitGenHandler;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitGenXunit$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitGenMap$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitGenArgs$;


    /**
     * The onNext handler for the tcMapperMap$
     *
     * @return handler to use for onNext for this.tcMapperMap$
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextMapStream() {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig tcfg = data.first;
            CompletionData<String> cd = data.second;
            tcfg.setMapping(cd.getResult());
            tcfg.completed.add("mapping");
            if (tcfg.completed.size() == 3)
                this.testCaseHandler.onNext(tcfg);
        };
    }

    /**
     * The onNext handler for the tcMapperJar$
     *
     * @return handler to use for onNext for this.tcMapperJar$
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextJarStream() {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig tcfg = data.first;
            CompletionData<String> cd = data.second;
            tcfg.setPathToJar(cd.getResult());
            tcfg.completed.add("jar");
            if (tcfg.completed.size() == 3)
                this.testCaseHandler.onNext(tcfg);
        };
    }

    /**
     * Creates handler for the tcMapperTC$
     *
     * @return handler to use for onNext for this.tcMapperTC$
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
                this.tcMapperTC$.onError(new Error("Could not serialize testcase args"));
            else {
                tcfg = new TestCaseConfig(maybeCfg);
                tcfg.setMapping(mapping);
                tcfg.setPathToJar(pathToJar);
                this.tcMapperArgs.put(cd.getId(), tcfg);
            }
            tcfg.completed.add("tc");
            if (tcfg.completed.size() == 3)
                this.testCaseHandler.onNext(tcfg);
        };
    }

    /**
     * Creates onNext handler for xunitGenXunit$ stream
     *
     * @return Consumer handler function
     */
    private Consumer<Tuple<XUnitConfig, CompletionData<String>>>
    nextXunitFileStream() {
        return ((Tuple<XUnitConfig, CompletionData<String>> data) -> {
            XUnitConfig xcfg = data.first;
            CompletionData<String> path = data.second;
            xcfg.setCurrentXUnit(path.getResult());
            xcfg.completed.add("xunit");
            if (xcfg.completed.size() == 3)
                this.xunitGenHandler.onNext(xcfg);
        });
    }

    /**
     * Creates onNext handler for xunitGenArgs$ stream
     *
     * @return Consumer handler function
     */
    private Consumer<Tuple<XUnitConfig, CompletionData<XUnitConfig>>>
    nextXargsStream() {
        return (Tuple<XUnitConfig, CompletionData<XUnitConfig>> data) -> {
            XUnitConfig xargs = data.first;
            CompletionData<XUnitConfig> cd = data.second;
            String mapping = xargs.getMapping();
            String xunitPath = xargs.getCurrentXUnit();
            XUnitConfig maybeCfg = cd.getResult();
            if (maybeCfg == null)
                this.xunitGenXunit$.onError(new Error("Could not serialize xunit args"));
            else {
                xargs = new XUnitConfig(maybeCfg);
                xargs.setMapping(mapping);
                xargs.setCurrentXUnit(xunitPath);
            }
            xargs.completed.add("xargs");
            if (xargs.completed.size() == 3)
                this.xunitGenHandler.onNext(xargs);
        };
    }

    /**
     * The onNext handler for the xunitGenMap$ stream
     *
     * @return handler to use for onNext
     */
    private Consumer<Tuple<XUnitConfig, CompletionData<String>>>
    nextXMapStream() {
        return (Tuple<XUnitConfig, CompletionData<String>> data) -> {
            XUnitConfig xcfg = data.first;
            CompletionData<String> cd = data.second;
            xcfg.setMapping(cd.getResult());
            xcfg.completed.add("mapping");
            if (xcfg.completed.size() == 3)
                this.xunitGenHandler.onNext(xcfg);
        };
    }

    private Consumer<? super Throwable> errHandler() {
        return err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        };
    }

    private Action compHandler(String msg) {
        return () -> logger.info(String.format("Got completion event for a %s call", msg));
    }

    /**
     * Creates and sets up subscribers for the various streams
     */
    private void setupStreams() {
        this.tcMapperMap$ = BehaviorSubject.create();    // stream for mapping.json upload
        this.tcMapperJar$ = BehaviorSubject.create();    // stream for jar upload
        this.tcMapperTC$ = BehaviorSubject.create();     // stream for testcase json upload
        this.testCaseHandler = BehaviorSubject.create(); // stream handler for /testcase/mapper

        this.xunitGenXunit$ = BehaviorSubject.create();  // stream for xunit xml upload
        this.xunitGenArgs$ = BehaviorSubject.create();   // stream for xargs json upload
        this.xunitGenMap$ = BehaviorSubject.create();    // stream for mapping.json upload
        this.xunitGenHandler = BehaviorSubject.create(); // stream handler for /xunit/generate

        // Sets up observers for the /testcase/mapper endpoint
        this.tcMapperMap$.subscribe(this.nextMapStream(), this.errHandler(), this.compHandler("map stream"));
        this.tcMapperJar$.subscribe(this.nextJarStream(), this.errHandler(), this.compHandler("jar stream"));
        this.tcMapperTC$.subscribe(this.nextTCStream(), this.errHandler(), this.compHandler("tc stream"));

        // Sets up observers for the /xunit/generate endpoint
        this.xunitGenMap$.subscribe(this.nextXMapStream(), this.errHandler(), this.compHandler("map stream"));
        this.xunitGenArgs$.subscribe(this.nextXargsStream(), this.errHandler(), this.compHandler("xargs stream"));
        this.xunitGenXunit$.subscribe(this.nextXunitFileStream(), this.errHandler(), this.compHandler("xunit"));
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

    private XUnitConfig checkXunitMapperArgs(CompletionData<String> data) {
        XUnitConfig xcfg;
        if (xunitMapperArgs.containsKey(data.getId()))
            xcfg = xunitMapperArgs.get(data.getId());
        else {
            xcfg = new XUnitConfig();
            xunitMapperArgs.put(data.getId(), xcfg);
        }
        return xcfg;
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

    private Handler<Void>
    xuploadHandler( String msg
            , String path
            , String type
            , UUID id
            , BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> stream) {
        return (Void) -> {
            logger.info(msg);
            CompletionData<String> data = new CompletionData<>(type, path, id);
            XUnitConfig xcfg = this.checkXunitMapperArgs(data);
            Tuple<XUnitConfig, CompletionData<String>> t = new Tuple<>(xcfg, data);
            stream.onNext(t);
        };
    }

    /**
     * Creates handler to be called when all form uploads have been completed
     *
     * This function will do the following if and when all the form uploads have completed
     * - Serialize a TestCaseConfig object
     * - Load the mapping.json
     * - Call MainReflector on the downloaded jar
     *
     * @param req HttpServerRequest
     * @return handler for /testcase/mapper
     */
    private Handler<Void> testcaseMapHandler(HttpServerRequest req) {
        return v -> this.testCaseHandler.subscribe((TestCaseConfig cfg) -> {
            JsonObject jo = new JsonObject();
            // Get the TestCaseConfig object
            if (cfg.completed.size() != 3) {
                logger.error("Haven't finished getting all info yet");
                return;
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
     * Creates handler that will be called when all the form uploads for the /xunit/generate have completed
     *
     * This function will do the following:
     * - Serialize the XunitConfig object
     * - Call XUnitReporter.createPolarionXunit()
     *
     * @param req HttpServerRequest
     * @return Handler for /xunit/generate
     */
    private Handler<Void> xunitGenerateHandler(HttpServerRequest req) {
        return v -> this.xunitGenHandler.subscribe(n -> {

        }, err -> {

        }, () -> {

        });
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
                    upload.endHandler(this.uploadHandler(msg, path, "mapping", id, this.tcMapperMap$));
                    break;
                case "jar":
                    String jarpath = FileHelper.makeTempFile("/tmp", "jarToCheck", ".jar", null).toString();
                    upload.streamToFileSystem(jarpath);
                    String jmsg = "jar file now fully uploaded";
                    upload.endHandler(this.uploadHandler(jmsg, jarpath, "jar", id, this.tcMapperJar$));
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
                        this.tcMapperTC$.onNext(t);
                    });
                    break;
                default:
                    logger.error("Unknown file attribute");
            }
        })
        .endHandler(this.testcaseMapHandler(req));
    }

    /**
     * Handles a JSON upload that will be serialized into an XUnitConfig object then passed to the xunitImportStream
     *
     * @param upload HttpSsrverFileUpload object
     */
    private void xargsUploadHandler(HttpServerFileUpload upload, UUID id) {
        // Instead of streaming to the filesystem then deserialize, just deserialize from buffer
        Buffer buff = Buffer.buffer();
        // As the file upload chunks come in, the next handler will append them to buff.  Once we have a
        // completion event, we can convert the buffer to a string, and deserialize into our object
        upload.toFlowable().subscribe(n -> {
            n.appendBuffer(buff);
        }, err -> {
            logger.error("Could not upload xargs file");
        }, () -> {
            logger.info("xargs json file has been fully uploaded");
            XUnitConfig xargs = null;
            try {
                xargs = Serializer.from(XUnitConfig.class, buff.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            CompletionData<XUnitConfig> data = new CompletionData<>("xargs", xargs, id);
            Tuple<XUnitConfig, CompletionData<XUnitConfig>> t = new Tuple<>(xargs, data);
            this.xunitGenArgs$.onNext(t);
        });
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
            String msg, path;
            switch(fname) {
                case "xunit":
                    path = FileHelper.makeTempFile("/tmp", "polarion-result-", ".xml", null).toString();
                    upload.streamToFileSystem(path);
                    msg = "xunit xml file now fully uploaded";
                    upload.endHandler(this.xuploadHandler(msg, path, "xunit", id, this.xunitGenXunit$));
                    break;
                case "xargs":
                    this.xargsUploadHandler(upload, id);
                    break;
                case "mapping":
                    path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    msg = "mapping.json now fully uploaded";
                    upload.endHandler(this.xuploadHandler(msg, path, "mapping", id, this.xunitGenMap$));
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
     * Takes an xunit file and uploads to the Polarion xunit import endpoint
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
            router.post("/xunit/generate").handler(this::xunitGenerator);
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
