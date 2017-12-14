package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.importer.XUnitService;
import com.github.redhatqe.polarizer.reporter.XUnitReporter;
import com.github.redhatqe.polarizer.reporter.configuration.api.IComplete;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.tests.APITestSuite;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
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
    private EventBus bus;
    private Map<UUID, TestCaseConfig> tcMapperArgs = new HashMap<>();
    private Map<UUID, XUnitConfig> xunitMapperArgs = new HashMap<>();

    // Streams for processing data
    private BehaviorSubject<XUnitGenData> xunitGenData$;
    private Map<UUID, Subject<XUnitGenData>> streamMap;

    private static void addToComplete(IComplete<String> first, IComplete<String> second) {
        first.getCompleted().stream()
                .filter(t -> !second.getCompleted().contains(t))
                .forEach(second::addToComplete);
    }

    /**
     * All the nextX handlers will do the following:
     *
     * Grab the appropriate Config type and CompletionData from the Tuple (that comes from the stream).  It will then
     * set a particular field in the config object.  But it also needs to check in the appropriate Map of UUID to
     * Config field, to see if we've already
     *
     * TODO: I think this is all wrong.  It should have been a filter + reduce + merge.  Filter messages coming from
     * the stream that match a UUID to create a new stream.  This new stream will perform a reduce on a TestConfig or
     * XUnitConfig object so that it "builds up" a complete instance.  The reduction will be done based on a completion
     * list.
     *
     * Take for example these events in the stream, where all the items in A have the same ID, all the items in B have
     * the same ID, etc.
     *
     * A is "complete" when the A.complete = {"mapping", "xunit", "jar"}
     * B is "complete" when the B.complete = {"xunit", "xargs"}
     * C is "complete" when C.complete = {"testcase", "xml"}
     *
     * stream$ = --A--B--A--C--A--B--A--C--B-->
     *
     * aStream$ = stream$.filter(i -> i.id == A.id)
     * bStream$ = stream$.filter(i -> i.id == B.id)
     * cStream$ = stream$.filter(i -> i.id == C.id)
     *
     * aStream$.reduce((acc, next) -> {
     *
     * })
     *
     * Observable.merge(aStream$, bStream$, cStream$
     *
     * @return
     */

    /**
     * Generic function to create an onError handler
     *
     * @return lambda useable for an Observer's onError
     */
    private Consumer<? super Throwable> errHandler() {
        return err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        };
    }

    /**
     * Generic function to create an onComplete handler
     *
     * @param msg info to pass to onComplete
     * @return lambda useable for Observer's onComplete
     */
    private Action compHandler(String msg) {
        return () -> logger.info(String.format("Got completion event for a %s call", msg));
    }

    private Consumer<XUnitGenData> xunitGenDataOnNext() {
        return gd -> {
            // Look up ID
            Subject<XUnitGenData> stream$ = this.streamMap.get(gd.getId());
            if (stream$ == null) {
                this.streamMap.put(gd.getId(),)
            }
        };
    }

    /**
     * Creates and sets up subscribers for the various streams
     */
    private void setupStreams() {
        this.streamMap = new HashMap<>();
        this.xunitGenData$ = BehaviorSubject.create();

        this.xunitGenData$.subscribe();
    }

    private <T, U> Optional<T> checkMapper(CompletionData<U> data, Map<UUID, T> map) {
        T tcfg = null;
        if (map.containsKey(data.getId()))
            tcfg = map.get(data.getId());
        return Optional.ofNullable(tcfg);
    }

    private void
    xargsUploader( HttpServerFileUpload upload
                 , XUnitGenData data) {
        // Instead of streaming to the filesystem then deserialize, just deserialize from buffer
        // As the file upload chunks come in, the next handler will append them to buff.  Once we have a
        // completion event, we can convert the buffer to a string, and deserialize into our object
        Buffer buff = Buffer.buffer();
        upload.toFlowable().subscribe(
                buff::appendBuffer,
                err -> logger.error("Could not upload xargs file"),
                () -> {
                    logger.info(String.format("xargs json for %s has been fully uploaded", data.getId().toString()));
                    XUnitConfig xargs;
                    try {
                        xargs = Serializer.from(XUnitConfig.class, buff.toString());
                        data.setConfig(xargs);
                        this.xunitGenData$.onNext(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                        this.xunitGenData$.onError(e);
                    }
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
        return v -> this.testCaseHandler.subscribe(item -> {
            UUID id = item.first;
            TestCaseConfig cfg = item.second;
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
            if (this.tcMapperArgs.remove(id) != null)
                logger.debug(String.format("Removing %s from tcMapperArgs", id.toString()));

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
        return v -> this.xunitImpHandler.subscribe(item -> {
            XUnitConfig n = item.second;
            UUID id = item.first;
            JsonObject jo;
            // Get the TestCaseConfig object
            if (n.completed.size() != 2) {
                logger.error("Haven't finished getting all info yet");
                return;
            }
            if (this.xunitMapperArgs.remove(id) != null)
                logger.debug(String.format("Removing %s from xunitMapperArgs", id.toString()));

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
        return v -> this.xunitGenHandler.subscribe(item -> {
            UUID id = item.first;
            XUnitConfig n = item.second;
            logger.info(String.format("In xunitGenerateHandler for %s", id.toString()));
            XUnitReporter.createPolarionXunit(n);
            String newxunit = n.getNewXunit();
            JsonObject jo = new JsonObject();
            jo.put("status", "success");
            jo.put("xunit", FileHelper.readFile(newxunit));

            if (this.xunitMapperArgs.remove(id) != null)
                logger.debug(String.format("Removing %s from xunitMapperArgs", id.toString()));

            req.response().end(jo.encode());
        }, err -> {
            JsonObject jo = new JsonObject();
            jo.put("status", "failed");
            req.response().end(jo.encode());
        }, () -> {
            logger.info("Completed xunitGenerateHandler");
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
        String base = "%s for %s now fully uploaded";
        req.uploadHandler(upload -> {
            String fName = upload.name();
            switch(fName) {
                case "mapping":
                    String path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    String msg = String.format(base, "mapping.json", id.toString());
                    //upload.endHandler(this.uploadHandler(msg, path, "mapping", id, this.tcMapperMap$));
                    upload.endHandler(this.uploadHdlr(msg, "mapping", path, id, TestCaseConfig.class,
                            this.tcMapperArgs, this.tcMapperMap$));
                    break;
                case "jar":
                    String jarpath = FileHelper.makeTempFile("/tmp", "jarToCheck", ".jar", null).toString();
                    upload.streamToFileSystem(jarpath);
                    String jmsg = String.format(base, "jar", id.toString());
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
                        logger.info(String.format(base, "testcase file", id.toString()));
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

    private void xunitGenerator(RoutingContext rc) {
        logger.info("In xunitGenerator");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        req.uploadHandler(upload -> {
            String fname = upload.name();
            String msg, path;
            XUnitGenData data;
            switch(fname) {
                case "xunit":
                    path = FileHelper.makeTempFile("/tmp", "polarion-result-", ".xml", null).toString();
                    upload.streamToFileSystem(path);
                    logger.info(String.format("xunit xml file for %s now fully uploaded", id.toString()));
                    data = new XUnitGenData(path, id);
                    this.xunitGenData$.onNext(data);
                    break;
                case "xargs":
                    data = new XUnitGenData(id);
                    this.xargsUploader(upload, data);
                    break;
                case "mapping":
                    path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    logger.info(String.format("mapping.json for %s now fully uploaded", id.toString()));
                    data = new XUnitGenData(id, path);
                    this.xunitGenData$.onNext(data);
                    break;
                default:
                    break;
            }
        }).endHandler(this.xunitGenerateHandler(req));

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
                    msg = String.format("xunit xml file for %s now fully uploaded", id.toString());
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
        logger.info("In testcaseImport");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();

    }

    /**
     * Makes a request to the APITestSuite verticle to run tests
     *
     * TODO:  Need to be able to differentiate suites to run.
     * TODO:  Need to be able to see the test results live (perhaps make this a websocket)
     *
     * @param ctx
     */
    public void test(RoutingContext ctx) {
        HttpServerRequest req = ctx.request();
        req.bodyHandler(upload -> {
            logger.info("Got the test config file");
            String body = upload.toString();
            // Send on event bus
            String address = APITestSuite.class.getCanonicalName();
            address = "APITestSuite";
            this.bus.send(address, body);
            //this.bus.rxSend(address, body)
            //        .subscribe(n -> logger.info(n.body().toString()),
            //                err -> logger.error(err));
            JsonObject jo = new JsonObject();
            jo.put("result", "Kicking off tests");
            req.response().end(jo.encode());
        });
    }

    public void start() {
        this.setupStreams();

        this.bus = vertx.eventBus();
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
