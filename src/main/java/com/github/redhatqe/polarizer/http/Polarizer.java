package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.importer.XUnitService;
import com.github.redhatqe.polarizer.reporter.configuration.api.IComplete;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
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
import io.vertx.reactivex.core.http.HttpServerResponse;
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
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitFile$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitMap$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitArgs$;

    private BehaviorSubject<XUnitConfig> xunitImpHandler;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitImpFile$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitImpArgs$;

    private <T extends IComplete, U> Consumer<Tuple<T, CompletionData<U>>>
    next$(int size, U result, BehaviorSubject<T> h$) {
        return (Tuple<T, CompletionData<U>> data) -> {
            T tcfg = data.first;
            CompletionData<U> cd = data.second;
            tcfg.setMapping(cd.getResult());
            tcfg.addToComplete(result);
            if (tcfg.completed() == size)
                h$.onNext(tcfg);
        };
    }

    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextStream(int size, String m, BehaviorSubject<TestCaseConfig> h$) {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig tcfg = data.first;
            CompletionData<String> cd = data.second;
            tcfg.setMapping(cd.getResult());
            tcfg.addToComplete(m);
            if (tcfg.completed() == size)
                h$.onNext(tcfg);
        };
    }


    /**
     * Creates handler for the tcMapperTC$
     *
     * @return handler to use for onNext for this.tcMapperTC$
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<TestCaseConfig>>>
    nextTCStream(BehaviorSubject<TestCaseConfig> h$) {
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
                h$.onNext(tcfg);
        };
    }

    /**
     * Creates onNext handler for xunitGenXunit$ stream
     *
     * NOTE: mostly used as next handler for uploads of xunit json/yml
     *
     * @return Consumer handler function
     */
    private Consumer<Tuple<XUnitConfig, CompletionData<String>>>
    nextXunitFileStream(int size, String m, BehaviorSubject<XUnitConfig> h$) {
        return ((Tuple<XUnitConfig, CompletionData<String>> data) -> {
            XUnitConfig xcfg = data.first;
            CompletionData<String> path = data.second;
            xcfg.setCurrentXUnit(path.getResult());
            xcfg.completed.add(m);
            if (xcfg.completed.size() == size)
                h$.onNext(xcfg);
        });
    }

    /**
     * Creates onNext handler for xunitGenArgs$ stream
     *
     * @return Consumer handler function
     */
    private Consumer<Tuple<XUnitConfig, CompletionData<XUnitConfig>>>
    nextXargsStream( int size
                   , BehaviorSubject<XUnitConfig> h$
                   , BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> e$) {
        return (Tuple<XUnitConfig, CompletionData<XUnitConfig>> data) -> {
            XUnitConfig xargs = data.first;
            CompletionData<XUnitConfig> cd = data.second;
            String mapping = xargs.getMapping();
            String xunitPath = xargs.getCurrentXUnit();
            XUnitConfig maybeCfg = cd.getResult();
            if (maybeCfg == null)
                e$.onError(new Error("Could not serialize xunit args"));
            else {
                xargs = new XUnitConfig(maybeCfg);
                xargs.setMapping(mapping);
                xargs.setCurrentXUnit(xunitPath);
            }
            xargs.completed.add("xargs");
            if (xargs.completed.size() == size)
                h$.onNext(xargs);
        };
    }

    /**
     * The onNext handler for the xunitGenMap$ stream
     *
     * @return handler to use for onNext
     */
    private Consumer<Tuple<XUnitConfig, CompletionData<String>>>
    nextXStream(int size, String m, BehaviorSubject<XUnitConfig> subj) {
        return (Tuple<XUnitConfig, CompletionData<String>> data) -> {
            XUnitConfig xcfg = data.first;
            CompletionData<String> cd = data.second;
            xcfg.setMapping(cd.getResult());
            xcfg.addToComplete(m);
            if (xcfg.completed() == size)
                subj.onNext(xcfg);
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
     * Sets up observers for the /testcase/mapper endpoint
     */
    private void setupTCMapperStreams() {
        this.tcMapperMap$ = BehaviorSubject.create();    // stream for mapping.json upload
        this.tcMapperJar$ = BehaviorSubject.create();    // stream for jar upload
        this.tcMapperTC$ = BehaviorSubject.create();     // stream for testcase json upload
        this.testCaseHandler = BehaviorSubject.create(); // stream handler for /testcase/mapper
        this.tcMapperMap$.subscribe(
                this.nextStream(3, "mapping", this.testCaseHandler),
                this.errHandler(),
                this.compHandler("map stream"));
        this.tcMapperJar$.subscribe(
                this.nextStream(3, "jar", this.testCaseHandler),
                this.errHandler(),
                this.compHandler("jar stream"));
        this.tcMapperTC$.subscribe(
                this.nextTCStream(this.testCaseHandler),
                this.errHandler(),
                this.compHandler("tc stream"));
    }

    /**
     * Sets up observers for the /xunit/generate endpoint
     */
    private void setupXunitGenStreams() {
        this.xunitFile$ = BehaviorSubject.create();  // stream for xunit xml upload
        this.xunitArgs$ = BehaviorSubject.create();   // stream for xargs json upload
        this.xunitMap$ = BehaviorSubject.create();    // stream for mapping.json upload
        this.xunitGenHandler = BehaviorSubject.create(); // stream handler for /xunit/generate
        this.xunitMap$.subscribe(
                this.nextXStream(3, "mapping", this.xunitGenHandler),
                this.errHandler(),
                this.compHandler("map stream"));
        this.xunitArgs$.subscribe(
                this.nextXargsStream(3, this.xunitGenHandler, this.xunitArgs$),
                this.errHandler(),
                this.compHandler("xargs stream"));
        this.xunitFile$.subscribe(
                this.nextXunitFileStream(3, "xunit", this.xunitGenHandler),
                this.errHandler(),
                this.compHandler("xunit"));
    }

    /**
     * Sets up observers for the /xunit/import endpoint
     */
    private void setupXunitImpStreams() {
        this.xunitImpFile$ = BehaviorSubject.create();
        this.xunitImpArgs$ = BehaviorSubject.create();
        this.xunitImpHandler = BehaviorSubject.create();
        this.xunitImpArgs$.subscribe(
                this.nextXargsStream(2, this.xunitImpHandler, this.xunitArgs$),
                this.errHandler(),
                this.compHandler("xargs stream"));
        this.xunitImpFile$.subscribe(
                this.nextXunitFileStream(2, "xunit", this.xunitImpHandler),
                this.errHandler(),
                this.compHandler("xunit"));
    }

    /**
     * Creates and sets up subscribers for the various streams
     */
    private void setupStreams() {
        this.setupTCMapperStreams();
        this.setupXunitGenStreams();
        this.setupXunitImpStreams();
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

    private <T, U> Optional<T> checkMapper(CompletionData<U> data, Map<UUID, T> map) {
        T tcfg = null;
        if (map.containsKey(data.getId()))
            tcfg = map.get(data.getId());
        return Optional.ofNullable(tcfg);
    }

    /**
     * Provides a handler
     *
     * @param msg message for logger
     * @param type 1st arg passed to CompletionData constructor
     * @param result 2nd arg passed to CompletionData constructor
     * @param id 3rd arg passed to CompletionData constructor
     * @param cls the class of the type to be stored as the value in the map arg
     * @param map a map from UUID to type T
     * @param stream a stream that takes a Tuple of a TestCaseConfig, and CompletionData of String
     * @param <T> The type to store in the map
     * @param <U> The type of the CompletionData
     * @return Handler\<Void\>
     */
    private <T, U> Handler<Void>
    uploadHdlr( String msg
              , String type
              , U result
              , UUID id
              , Class<T> cls
              , Map<UUID, T> map
              , Subject<Tuple<T, CompletionData<U>>> stream) {
        return (Void) -> {
            logger.info(msg);
            CompletionData<U> data = new CompletionData<>(type, result, id);
            Optional<T> mCfg = this.checkMapper(data, map);
            T tcfg = mCfg.orElseGet(() -> {
                T cfg = null;
                try {
                    cfg = cls.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    logger.error("Could not instantiate new instance");
                    e.printStackTrace();
                }
                map.put(data.getId(), cfg);
                return cfg;
            });
            Tuple<T, CompletionData<U>> t = new Tuple<>(tcfg, data);
            stream.onNext(t);
        };
    }

    /**
     * Handles a JSON upload that will be serialized into an XUnitConfig object then passed to the xunitImportStream
     *
     * @param upload HttpSsrverFileUpload object
     */
    private void xargsUploadHandler( HttpServerFileUpload upload
                                   , UUID id
                                   , BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> arg$) {
        // Instead of streaming to the filesystem then deserialize, just deserialize from buffer
        Buffer buff = Buffer.buffer();
        // As the file upload chunks come in, the next handler will append them to buff.  Once we have a
        // completion event, we can convert the buffer to a string, and deserialize into our object
        upload.toFlowable().subscribe(
                buff::appendBuffer,
                err -> logger.error("Could not upload xargs file"),
                () -> {
                    logger.info("xargs json file has been fully uploaded");
                    XUnitConfig xargs = null;
                    logger.info(buff.toString());
                    try {
                        xargs = Serializer.from(XUnitConfig.class, buff.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    CompletionData<XUnitConfig> data = new CompletionData<>("xargs", xargs, id);
                    Tuple<XUnitConfig, CompletionData<XUnitConfig>> t = new Tuple<>(xargs, data);
                    arg$.onNext(t);
                });
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
     * - Call XUnitService.createPolarionXunit()
     *
     * @param req HttpServerRequest
     * @return Handler for /xunit/generate
     */
    private Handler<Void> xunitImportHandler(HttpServerRequest req) {
        return v -> this.xunitImpHandler.subscribe((XUnitConfig n) -> {
            JsonObject jo;
            // Get the TestCaseConfig object
            if (n.completed.size() != 3) {
                logger.error("Haven't finished getting all info yet");
                return;
            }
            jo = XUnitService.request(n);
            HttpServerResponse resp = req.response();
            resp.end(jo.encode());
        }, err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        }, () -> {
            logger.info("Completion event for xunit import");
        });
    }

    /**
     * Creates handler that will be called when all the form uploads for the /xunit/import have completed
     *
     * This function will do the following:
     * - Serialize the XunitConfig object
     * - Call XUnitService.request()
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
                    //upload.endHandler(this.uploadHandler(msg, path, "mapping", id, this.tcMapperMap$));
                    upload.endHandler(this.uploadHdlr(msg, "mapping", path, id, TestCaseConfig.class,
                            this.tcMapperArgs, this.tcMapperMap$));
                    break;
                case "jar":
                    String jarpath = FileHelper.makeTempFile("/tmp", "jarToCheck", ".jar", null).toString();
                    upload.streamToFileSystem(jarpath);
                    String jmsg = "jar file now fully uploaded";
                    //upload.endHandler(this.uploadHandler(jmsg, jarpath, "jar", id, this.tcMapperJar$));
                    upload.endHandler(this.uploadHdlr(jmsg, "jar", jarpath, id, TestCaseConfig.class,
                            this.tcMapperArgs, this.tcMapperJar$));
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
                    //upload.endHandler(this.xuploadHandler(msg, path, "xunit", id, this.xunitFile$));
                    upload.endHandler(this.uploadHdlr(msg, "xunit", path, id, XUnitConfig.class,
                            this.xunitMapperArgs, this.xunitFile$));
                    break;
                case "xargs":
                    this.xargsUploadHandler(upload, id, this.xunitArgs$);
                    break;
                case "mapping":
                    path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    msg = "mapping.json now fully uploaded";
                    //upload.endHandler(this.xuploadHandler(msg, path, "mapping", id, this.xunitMap$));
                    upload.endHandler(this.uploadHdlr(msg, "mapping", path, id, XUnitConfig.class,
                            this.xunitMapperArgs, this.xunitMap$));
                    break;
                default:
                    break;
            }
        });

        // TODO: make call to the importerRequest
    }

    /**
     * Takes an xunit file and uploads to the Polarion xunit import endpoint
     *
     * @param rc context passed by server
     */
    private void xunitImport(RoutingContext rc) {
        logger.info("In xunitImport");
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
                    upload.endHandler(this.uploadHdlr(msg, "xunit", path, id, XUnitConfig.class,
                            this.xunitMapperArgs, this.xunitImpFile$));
                    break;
                case "xargs":
                    this.xargsUploadHandler(upload, id, this.xunitImpArgs$);
                    break;
                default:
                    break;
            }
        }).endHandler(this.xunitImportHandler(req));
    }

    /**
     *
     * @param rc context passed by server
     */
    private void testcaseImport(RoutingContext rc) {

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
