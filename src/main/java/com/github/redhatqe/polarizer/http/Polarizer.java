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
import io.reactivex.Flowable;
import io.reactivex.Observer;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
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
    private BehaviorSubject<Tuple<UUID, TestCaseConfig>> testCaseHandler;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> tcMapperMap$;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<String>>> tcMapperJar$;
    private BehaviorSubject<Tuple<TestCaseConfig, CompletionData<TestCaseConfig>>> tcMapperTC$;

    private BehaviorSubject<Tuple<UUID, XUnitConfig>> xunitGenHandler;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitFile$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitMap$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitArgs$;

    private BehaviorSubject<Tuple<UUID, XUnitConfig>> xunitImpHandler;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<String>>> xunitImpFile$;
    private BehaviorSubject<Tuple<XUnitConfig, CompletionData<XUnitConfig>>> xunitImpArgs$;

    private BehaviorSubject<XUnitGenData> xunitGenData$;

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
    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextMapHdlr() {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig cfg = data.first;
            CompletionData<String> cd = data.second;
            cfg.setMapping(cd.getResult());

            Optional<TestCaseConfig> mCfg = this.checkMapper(cd, this.tcMapperArgs);
            mCfg.ifPresent(c -> {
                cfg.setPathToJar(c.getPathToJar());
                Polarizer.addToComplete(c, cfg);
            });

            if (!cfg.completed.contains("mapping"))
                cfg.addToComplete("mapping");
            this.tcMapperArgs.put(cd.getId(), cfg);

            if (cfg.completed() == 3) {
                Tuple<UUID, TestCaseConfig> arg = new Tuple<>(cd.getId(), cfg);
                this.testCaseHandler.onNext(arg);
            }
        };
    }

    private Consumer<Tuple<TestCaseConfig, CompletionData<String>>>
    nextJarHdlr() {
        return (Tuple<TestCaseConfig, CompletionData<String>> data) -> {
            TestCaseConfig cfg = data.first;
            CompletionData<String> cd = data.second;
            cfg.setPathToJar(cd.getResult());

            Optional<TestCaseConfig> mCfg = this.checkMapper(cd, this.tcMapperArgs);
            mCfg.ifPresent(c -> {
                cfg.setMapping(c.getMapping());
                Polarizer.addToComplete(c, cfg);
            });

            if (!cfg.completed.contains("jar"))
                cfg.addToComplete("jar");
            this.tcMapperArgs.put(cd.getId(), cfg);

            if (cfg.completed() == 3) {
                Tuple<UUID, TestCaseConfig> arg = new Tuple<>(cd.getId(), cfg);
                this.testCaseHandler.onNext(arg);
            }
        };
    }

    private Consumer<Tuple<XUnitConfig, CompletionData<String>>>
    nextXMapHdlr() {
        return (Tuple<XUnitConfig, CompletionData<String>> data) -> {
            XUnitConfig cfg = data.first;
            CompletionData<String> cd = data.second;
            cfg.setMapping(cd.getResult());

            Optional<XUnitConfig> mCfg = this.checkMapper(cd, this.xunitMapperArgs);
            mCfg.ifPresent(c -> {
                cfg.setCurrentXUnit(c.getCurrentXUnit());
                Polarizer.addToComplete(c, cfg);
            });

            if (!cfg.completed.contains("mapping"))
                cfg.addToComplete("mapping");
            this.xunitMapperArgs.put(cd.getId(), cfg);

            if (cfg.completed() == 3) {
                Tuple<UUID, XUnitConfig> arg = new Tuple<>(cd.getId(), cfg);
                this.xunitGenHandler.onNext(arg);
            }
        };
    }

    /**
     * Creates handler for the tcMapperTC$
     *
     * @return handler to use for onNext for this.tcMapperTC$
     */
    private Consumer<Tuple<TestCaseConfig, CompletionData<TestCaseConfig>>>
    nextTCStream(BehaviorSubject<Tuple<UUID, TestCaseConfig>> h$) {
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
            }

            Optional<TestCaseConfig> mCfg = this.checkMapper(cd, this.tcMapperArgs);
            if (mCfg.isPresent()) {
                TestCaseConfig curr = mCfg.get();
                tcfg.setMapping(curr.getMapping());
                tcfg.setPathToJar(curr.getPathToJar());
                Polarizer.addToComplete(curr, tcfg);
            }

            if (!tcfg.completed.contains("tc"))
                tcfg.completed.add("tc");
            this.tcMapperArgs.put(cd.getId(), tcfg);

            if (tcfg.completed.size() == 3) {
                Tuple<UUID, TestCaseConfig> item = new Tuple<>(cd.getId(), tcfg);
                h$.onNext(item);
            }
        };
    }


    private Consumer<Tuple<XUnitConfig, CompletionData<XUnitConfig>>>
    nextXargsStream(int size
                   , String type
                   , Subject<Tuple<UUID, XUnitConfig>> h$) {
        return (Tuple<XUnitConfig, CompletionData<XUnitConfig>> data) -> {
            XUnitConfig xargs = data.first;
            CompletionData<XUnitConfig> cd = data.second;
            Optional<XUnitConfig> mCfg = this.checkMapper(cd, this.xunitMapperArgs);
            if (mCfg.isPresent()) {
                XUnitConfig curr = mCfg.get();
                xargs.setCurrentXUnit(curr.getCurrentXUnit());
                xargs.setMapping(curr.getMapping());
                Polarizer.addToComplete(curr, xargs);
            }

            if (!xargs.completed.contains("xargs"))
                xargs.addToComplete("xargs");
            this.xunitMapperArgs.put(cd.getId(), xargs);

            if (xargs.completed.size() == size) {
                Tuple<UUID, XUnitConfig> item = new Tuple<>(cd.getId(), xargs);
                h$.onNext(item);
            }
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
    nextXunitFileStream(int size
                       , String type
                       , Subject<Tuple<UUID, XUnitConfig>> h$) {
        return ((Tuple<XUnitConfig, CompletionData<String>> data) -> {
            XUnitConfig xcfg = data.first;
            CompletionData<String> path = data.second;
            xcfg.setCurrentXUnit(path.getResult());

            Optional<XUnitConfig> mCfg = this.checkMapper(path, this.xunitMapperArgs);
            if (mCfg.isPresent()) {
                XUnitConfig curr = mCfg.get();
                xcfg.setCurrentXUnit(curr.getCurrentXUnit());
                xcfg.setMapping(curr.getMapping());
                Polarizer.addToComplete(curr, xcfg);
            }

            if (!xcfg.completed.contains(type))
                xcfg.addToComplete(type);
            this.xunitMapperArgs.put(path.getId(), xcfg);

            if (xcfg.completed.size() == size) {
                Tuple<UUID, XUnitConfig> item = new Tuple<>(path.getId(), xcfg);
                h$.onNext(item);
            }
        });
    }

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

    /**
     * Sets up observers for the /testcase/mapper endpoint
     */
    private void setupTCMapperStreams() {
        this.tcMapperMap$ = BehaviorSubject.create();    // stream for mapping.json upload
        this.tcMapperJar$ = BehaviorSubject.create();    // stream for jar upload
        this.tcMapperTC$ = BehaviorSubject.create();     // stream for testcase json upload
        this.testCaseHandler = BehaviorSubject.create(); // stream handler for /testcase/mapper
        this.tcMapperMap$.subscribe(
                this.nextMapHdlr(),
                this.errHandler(),
                this.compHandler("map stream"));
        this.tcMapperJar$.subscribe(
                this.nextJarHdlr(),
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
                this.nextXMapHdlr(),
                this.errHandler(),
                this.compHandler("map stream"));
        this.xunitArgs$.subscribe(
                this.nextXargsStream(3, "xargs", this.xunitGenHandler),
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
                this.nextXargsStream(2, "xargs", this.xunitImpHandler),
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

        this.xunitGenData$ = BehaviorSubject.create();
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

    private <T, U> Handler<Void>
    uploadHdlr( String msg
              , CompletionData<U> data
              , Class<T> cls
              , Map<UUID, T> map
              , Subject<Tuple<T, CompletionData<U>>> stream) {
        return (Void) -> {
            logger.info(msg);
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
                    logger.info(String.format("xargs json file for %s has been fully uploaded", id.toString()));
                    XUnitConfig xargs = null;
                    //logger.info(buff.toString());
                    try {
                        xargs = Serializer.from(XUnitConfig.class, buff.toString());
                        CompletionData<XUnitConfig> data = new CompletionData<>("xargs", xargs, id);
                        Tuple<XUnitConfig, CompletionData<XUnitConfig>> t = new Tuple<>(xargs, data);
                        this.xunitMapperArgs.put(id, xargs);
                        arg$.onNext(t);
                    } catch (IOException e) {
                        e.printStackTrace();
                        arg$.onError(e);
                    }
                });
    }

    private void
    xargsUploader( HttpServerFileUpload upload
                 , XUnitGenData data
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
                    logger.info(String.format("xargs json file for %s has been fully uploaded", id.toString()));
                    XUnitConfig xargs;
                    try {
                        xargs = Serializer.from(XUnitConfig.class, buff.toString());
                        //CompletionData<XUnitConfig> data = new CompletionData<>("xargs", xargs, id);
                        data.setConfig(xargs);
                        data.setId(id);
                        
                    } catch (IOException e) {
                        e.printStackTrace();
                        arg$.onError(e);
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
                    msg = String.format("xunit xml file for %s now fully uploaded", id.toString());
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
                    msg = String.format("mapping.json for %s now fully uploaded", id.toString());
                    // Create a CompletionData type and pass to the xunitMap$ stream
                    CompletionData<String> d = new CompletionData<>("mapping", path, id);
                    upload.endHandler(this.uploadHdlr(msg, d, XUnitConfig.class, this.xunitMapperArgs, this.xunitMap$));
                    break;
                default:
                    break;
            }
        }).endHandler(this.xunitGenerateHandler(req));

        // TODO: make call to the importerRequest
    }

    private void xunitGen(RoutingContext rc) {
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
                    msg = String.format("xunit xml file for %s now fully uploaded", id.toString());
                    data = new XUnitGenData();
                    data.setXunitPath(msg);
                    this.xunitGenData$.onNext(data);
                    break;
                case "xargs":
                    this.xargsUploadHandler(upload, id, this.xunitArgs$);
                    break;
                case "mapping":
                    path = FileHelper.makeTempFile("/tmp", "mapping", ".json", null).toString();
                    upload.streamToFileSystem(path);
                    msg = String.format("mapping.json for %s now fully uploaded", id.toString());
                    // Create a CompletionData type and pass to the xunitMap$ stream
                    CompletionData<String> d = new CompletionData<>("mapping", path, id);
                    upload.endHandler(this.uploadHdlr(msg, d, XUnitConfig.class, this.xunitMapperArgs, this.xunitMap$));
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
