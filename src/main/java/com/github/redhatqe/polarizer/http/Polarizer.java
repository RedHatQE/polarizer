package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.data.Serializer;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.observables.GroupedObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestCase;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
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
    private BehaviorSubject<CompletionData> tcCompletions;
    private Map<UUID, TestCaseConfig> tcMapperArgs = new HashMap<>();
    private BehaviorSubject<TestCaseConfig> testCaseHandler;

    /**
     *
     * @return
     */
    private Consumer<? super CompletionData> nextCompletion() {
        return (CompletionData cd) -> {
            TestCaseConfig tcfg;
            if (tcMapperArgs.containsKey(cd.getId())) {
                tcfg = tcMapperArgs.get(cd.getId());
                switch(cd.getType()) {
                    case "mapping":
                        tcfg.setMapping((String) cd.getResult());
                        tcfg.completed.add("mapping");
                        break;
                    case "jar":
                        tcfg.setPathToJar((String) cd.getResult());
                        tcfg.completed.add("jar");
                        break;
                    case "tc":
                        String mapping = tcfg.getMapping();
                        String pathToJar = tcfg.getPathToJar();
                        TestCaseConfig maybeCfg = (TestCaseConfig) cd.getResult();
                        if (maybeCfg == null)
                            this.tcCompletions.onError(new Error("Could not serialize testcase args"));
                        else {
                            tcfg = new TestCaseConfig(maybeCfg);
                            tcfg.setMapping(mapping);
                            tcfg.setPathToJar(pathToJar);
                        }
                        tcfg.completed.add("tc");
                        break;
                    default:
                        break;
                }
                if (tcfg.completed.size() == 3) {
                    this.tcCompletions.onComplete();
                    // TODO:
                    this.testCaseHandler.onNext(tcfg);
                }
            }
            else {
                if (cd.getType().equals("tc"))
                    tcfg = (TestCaseConfig) cd.getResult();
                else
                    tcfg = new TestCaseConfig();
                tcfg.completed.add(cd.getType());
                tcMapperArgs.put(cd.getId(), tcfg);
            }
        };
    }

    private Consumer<? super Throwable> errCompletion() {
        return err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        };
    }

    private Action compCompletion() {
        return () -> {
            logger.info("Got completion event for a testcase mapper call");
        };
    }

    public void start() {
        VertxOptions opts = new VertxOptions();
        opts.setBlockedThreadCheckInterval(120000);
        this.vertx = Vertx.vertx(opts);

        TestCaseConfig cfg = new TestCaseConfig();
        this.tcCompletions = BehaviorSubject.create();
        this.testCaseHandler = BehaviorSubject.create();
        this.tcCompletions.subscribe(this.nextCompletion(), this.errCompletion(), this.compCompletion());

        EventBus bus = vertx.eventBus();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 9090);
        server.requestHandler(req -> {
                req.setExpectMultipart(true);
                router.route("/testcase/mapper").method(HttpMethod.POST).handler(this::testCaseMapper);
                router.post("/xunit/generator").handler(this::xunitGenerator);
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


    public void test(RoutingContext ctx) {
        // TODO: send msg on event bus to the APITestSuite verticle
        HttpServerRequest req = ctx.request();

        req.endHandler(resp -> {
            JsonObject jo = new JsonObject();
            jo.put("result", "TODO: make event bus message to APITestSuite");
            req.response().end(jo.encode());
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
        logger.info("Testing testcaseMapper2!");
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
                    upload.endHandler(v -> {
                        logger.info("mapping.json now fully uploaded");
                        CompletionData<String> data = new CompletionData<>("mapping", path, id);
                        this.tcCompletions.onNext(data);
                    });
                    break;
                case "jar":
                    File argPath = FileHelper.makeTempFile("/tmp", "jarToCheck", ".jar", null);
                    String jarpath = argPath.toString();
                    upload.streamToFileSystem(jarpath);
                    upload.endHandler(v -> {
                        logger.info("jar file now fully uploaded");
                        CompletionData<String> data = new CompletionData<>("jar", jarpath, id);
                        this.tcCompletions.onNext(data);
                    });
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
                        this.tcCompletions.onNext(data);
                    });
                    break;
                default:
                    logger.error("Unknown file attribute");
            }
        });

        // TODO: Once everything is uploaded, we need to do the following:
        // - Load the tcConfig file
        // - Load the mapping.json
        // - Call MainReflector on the downloaded jar
        req.endHandler(v -> this.testCaseHandler.subscribe(cfg -> {
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
            req.response().end(jo.encode());
        }, err -> {
            err.printStackTrace();
            logger.error(err.getMessage());
        }, () -> {
            logger.info("Done processing REST call");
        }));
    }


    /**
     * This method will generate an XML xunit file compatible with the XUnit Importer
     *
     * The body of the context will contain the following:
     * - a regular xunit xml file
     * - a JSON dict supplying extra parameters
     * - a mapping.json file
     *
     * @param rc context passed by server
     */
    private void xunitGenerator(RoutingContext rc) {

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

    public void bufferToJar(Buffer body, String name) {
        logger.info(String.format("Size of file is %d", body.length()));

        // TODO: We should probably relay the completion/error of buffering the jar to a Rx.Subject
        vertx.fileSystem().writeFile(UPLOAD_DIR + "/" + name, body, ar -> {
            if (ar.succeeded())
                logger.info("File written");
            else
                logger.error("Could not write file");
        });
    }

    class Foo {
        public Integer key;
        public String val;

        Foo(Integer k, String v) {
            this.key = k;
            this.val = v;
        }
    }
}
