package com.github.redhatqe.polarizer.http;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.observables.GroupedObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;


public class Polarizer extends AbstractVerticle {

    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String UPLOAD_DIR = "/tmp";
    private Disposable serverDisp;
    private BehaviorSubject<CompletionData> completions;

    /**
     *
     * @return
     */
    private Consumer<? super CompletionData> nextCompletion() {
        return cd -> {

        };
    }

    private Consumer<? super Throwable> errCompletion() {
        return err -> {

        };
    }

    private Action compCompletion() {
        return () -> {

        };
    }

    public void start() {
        this.completions = BehaviorSubject.create();
        Observable<List<GroupedObservable<UUID, CompletionData>>> grouped;
        grouped = this.completions.groupBy(CompletionData::getId).buffer(3);
        // reduce
        grouped.map( g -> {
          return null;
        });

        EventBus bus = vertx.eventBus();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server.requestHandler(req -> {
                req.setExpectMultipart(true);
                router.route("/testcase/mapper").method(HttpMethod.POST).handler(this::testCaseMapper);
                router.post("/xunit/generator").handler(this::xunitGenerator);
                router.post("/testcase/import").handler(this::testcaseImport);

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
         * TODO: Each of these files needs a unique name since multiple clients might be making concurrent requests
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
                    String path = "/tmp/" + "mapping.json";
                    upload.streamToFileSystem(path);
                    upload.endHandler(v -> {
                        logger.info("mapping.json now fully uploaded");
                        CompletionData<String> data = new CompletionData<>("mapping", path, id);
                        this.completions.onNext(data);
                    });
                    break;
                case "jar":
                    String jarpath = "/tmp/" + "jarToCheck.jar";
                    upload.streamToFileSystem(jarpath);
                    upload.endHandler(v -> {
                        logger.info("jar file now fully uploaded");
                        CompletionData<String> data = new CompletionData<>("jar", jarpath, id);
                        this.completions.onNext(data);
                    });
                    break;
                case "testcase":
                    String configPath = "/tmp/" + "tcArgs.yaml";
                    upload.streamToFileSystem(configPath);
                    // Convert the uploaded file to the args 
                    upload.endHandler(v -> {
                        logger.info("tcConfig file now fully uploaded");
                        CompletionData<String> data = new CompletionData<>("jar", configPath, id);
                        this.completions.onNext(data);
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
        req.endHandler(v -> {
            JsonObject jo = new JsonObject();
            jo.put("result", "congratulations");
            req.response().end(jo.encode());
        });
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
}
