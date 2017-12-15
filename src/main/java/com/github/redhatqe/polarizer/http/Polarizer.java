package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.http.data.IComplete;
import com.github.redhatqe.polarizer.http.data.TestCaseData;
import com.github.redhatqe.polarizer.http.data.XUnitData;
import com.github.redhatqe.polarizer.http.data.XUnitGenData;
import com.github.redhatqe.polarizer.importer.XUnitService;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.reporter.XUnitReporter;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.tests.APITestSuite;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.WorkerExecutor;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.file.FileSystem;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerFileUpload;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


public class Polarizer extends AbstractVerticle {

    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String UPLOAD_DIR = "/tmp";
    private EventBus bus;

    private <T extends XUnitData> void
    xargsUploader( HttpServerFileUpload upload
                 , T data
                 , ObservableEmitter<T> emitter) {
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
                        data.addToComplete("xargs");
                        emitter.onNext(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                        emitter.onError(e);
                    }
                });
    }

    private <T extends IComplete, U> void
    argsUploader( HttpServerFileUpload upload
                , Tuple<String, UUID> t
                , T data
                , Class<U> cls
                , Consumer<U> fn
                , ObservableEmitter<T> emitter) {
        // Instead of streaming to the filesystem then deserialize, just deserialize from buffer
        // As the file upload chunks come in, the next handler will append them to buff.  Once we have a
        // completion event, we can convert the buffer to a string, and deserialize into our object
        Buffer buff = Buffer.buffer();
        upload.toFlowable().subscribe(
                buff::appendBuffer,
                err -> logger.error(String.format("Could not upload %s file for %s", t.first, t.second.toString())),
                () -> {
                    logger.info(String.format("%s file for %s has been fully uploaded", t.first, t.second.toString()));
                    U xargs;
                    try {
                        xargs = Serializer.from(cls, buff.toString());
                        fn.accept(xargs);
                        data.addToComplete(t.first);
                        emitter.onNext(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                        emitter.onError(e);
                    }
                });
    }

    private <T extends IComplete> void
    fileUploader( HttpServerFileUpload upload
                , Tuple<String, UUID> t
                , Path path
                , T data
                , ObservableEmitter<T> emitter
                , Consumer<String> fn) {
        Buffer buff = Buffer.buffer();
        logger.debug("upload object: "  + upload.toString());
        upload.toFlowable().subscribe(
                buff::appendBuffer,
                err -> logger.error(String.format("Could not upload %s file", t.first)),
                () -> {
                    logger.info(String.format("%s file for %s has been fully uploaded", t.first, t.second));
                    try {
                        FileHelper.writeFile(path, buff.toString());
                        fn.accept(path.toString());
                        data.addToComplete(t.first);
                        emitter.onNext(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                        emitter.onError(e);
                    }
                });
    }

    private Observable<XUnitGenData> makeXGDObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    Path path;
                    XUnitGenData data;
                    Tuple<String, UUID> t;
                    switch (fname) {
                        case "xunit":
                            path = FileHelper.makeTempPath("/tmp", "polarion-result-", ".xml", null);
                            t = new Tuple<>("xunit", id);
                            data = new XUnitGenData(id);
                            this.fileUploader(upload, t, path, data, emitter, data::setXunitPath);
                            break;
                        case "xargs":
                            data = new XUnitGenData(id);
                            this.xargsUploader(upload, data, emitter);
                            break;
                        case "mapping":
                            path = FileHelper.makeTempPath("/tmp", "mapping-", ".json", null);
                            t = new Tuple<>("mapping", id);
                            data = new XUnitGenData(id);
                            this.fileUploader(upload, t, path, data, emitter, data::setMapping);
                            break;
                        default:
                            break;
                    }
                });
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    private void xunitGenerator(RoutingContext rc) {
        logger.info("In xunitGenerator");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        Observable<XUnitGenData> s$ = this.makeXGDObservable(id, req);
        // Scan is like a continuing reduce
        s$.scan(XUnitGenData::merge)
                .subscribe(xgd -> {
                    if (xgd.done()) {
                        XUnitConfig config = xgd.getConfig();
                        config.setCurrentXUnit(xgd.getXunitPath());
                        config.setMapping(xgd.getMapping());
                        XUnitReporter.createPolarionXunit(config);

                        JsonObject jo = new JsonObject();
                        try {
                            jo.put("newxunit", FileHelper.readFile(config.getNewXunit()));
                            jo.put("status", "passed");
                            req.response().end(jo.encode());
                        } catch (IOException e) {
                            e.printStackTrace();
                            jo.put("status", "failed");
                            req.response().end(jo.encode());
                        }
                    }
                    else {
                        logger.info("Not all parameters uploaded yet");
                        logger.info(xgd.getCompleted());
                    }
                }, err -> {
                    logger.error("Failure getting uploaded data " + err.getMessage());
                    JsonObject jo = new JsonObject();
                    jo.put("status", "error");
                    req.response().end(jo.encode());
                }, () -> {

                });
    }

    private Observable<XUnitData> makeXImpObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    XUnitData data;
                    switch (fname) {
                        case "xunit":
                            Path path = FileHelper.makeTempPath("/tmp", "polarion-result-", ".xml", null);
                            Tuple<String, UUID> t = new Tuple<>("xunit", id);
                            data = new XUnitData(id);
                            this.fileUploader(upload, t, path, data, emitter, data::setXunitPath);
                            break;
                        case "xargs":
                            data = new XUnitGenData(id);
                            this.xargsUploader(upload, data, emitter);
                            break;
                        default:
                            break;
                    }
                });
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    /**
     * Handler for the /xunit/import endpoint.
     *
     * @param rc context passed by server
     */
    private void xunitImport(RoutingContext rc) {
        logger.info("In xunitImport");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        Observable<XUnitData> s$ = this.makeXImpObservable(id, req);
        // Once we have a "complete" XUnitData object, make a XUnitService request.  Once that is complete, send a
        // response back.  The XUnitService.request() is performed in a worker verticle since it blocks
        // TODO: Make this a websocket since it can take a long time for XUnitService.request to complete
        s$.scan(XUnitData::merge)
                .subscribe((XUnitData xu) -> {
                    if (xu.done()) {
                        WorkerExecutor executor = vertx.createSharedWorkerExecutor("XUnitService.request");
                        executor.rxExecuteBlocking((Future<JsonObject> fut) -> {
                            try {
                                JsonObject jo = XUnitService.request(xu.getConfig());
                                fut.complete(jo);
                            } catch (IOException e) {
                                e.printStackTrace();
                                fut.fail(e);
                            }
                        }).subscribe(item -> {
                            req.response().end(item.encode());
                        });
                    }
                }, err -> {
                    JsonObject jo = new JsonObject();
                    String msg = "Error with upload";
                    logger.error(msg);
                    jo.put("status", "failed");
                    req.response().end(jo.encode());
                });
    }

    private Observable<TestCaseData> makeTCMapperObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    TestCaseData data = new TestCaseData(id);
                    Path path;
                    Tuple<String, UUID> t;
                    switch (fname) {
                        case "jar":
                            path = FileHelper.makeTempPath("/tmp", "jar-to-check-", ".jar", null);
                            t = new Tuple<>("jar", id);
                            this.fileUploader(upload, t, path, data, emitter, data::setJarToCheck);
                            break;
                        case "mapping":
                            path = FileHelper.makeTempPath("/tmp","/tmp/mapping-", ".json", null);
                            t = new Tuple<>("mapping", id);
                            this.fileUploader(upload, t, path, data, emitter, data::setMapping);
                            break;
                        case "xargs":
                            t = new Tuple<>("tcargs", id);
                            this.argsUploader(upload, t, data, TestCaseConfig.class, data::setConfig, emitter);
                            break;
                        default:
                            break;
                    }
                });
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    /**
     *
     * @param rc context passed by server
     */
    private void testCaseMapper(RoutingContext rc) {
        logger.info("In testcaseMapper");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        Observable<TestCaseData> s$ = this.makeTCMapperObservable(id, req);
        s$.scan(TestCaseData::merge)
                .subscribe(data -> {
                    if (data.done()) {
                        JsonObject jo;
                        TestCaseConfig cfg = data.getConfig();
                        try {
                            jo = MainReflector.process(cfg);
                            jo.put("result", "passed");
                        } catch (IOException ex) {
                            jo = new JsonObject();
                            jo.put("result", "failed");
                        }
                        req.response().end(jo.encode());
                    }
                }, err -> {
                    logger.error("Failed uploading necessary files");
                    JsonObject jo = new JsonObject();
                    jo.put("result", "error");
                    jo.put("message", "Failed uploading necessary files");
                });
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
            // Send message on event bus to the APITestSuite Verticle
            String address = APITestSuite.class.getCanonicalName();
            // FIXME: Tried using rxSend() but got an error that no consumer was registered
            this.bus.send(address, body);
            JsonObject jo = new JsonObject();
            jo.put("result", "Kicking off tests");
            req.response().end(jo.encode());
        });
    }

    public void start() {
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
