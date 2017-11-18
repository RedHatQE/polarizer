package com.github.redhatqe.polarizer.http;

import io.reactivex.disposables.Disposable;
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

import java.nio.file.Paths;
import java.util.UUID;


public class Polarizer extends AbstractVerticle {

    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String UPLOAD_DIR = "/tmp";
    private Disposable serverDisp;
    private BehaviorSubject<CompletionData> completions;

    class CompletionData {
        String type;
        String result;
        final UUID id;

        CompletionData(UUID id) {
            this.type = "";
            this.result = "";
            this.id = id;
        }

        CompletionData(String t, String r, UUID id) {
            this.type = t;
            this.result = r;
            this.id = id;
        }
    }


    public void start() {
        this.completions = BehaviorSubject.create();
        EventBus bus = vertx.eventBus();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server.requestHandler(req -> {
                req.setExpectMultipart(true);
                router.route("/testcase/mapper2").method(HttpMethod.POST).handler(this::testCaseMapper2);
                router.post("/testcase/mapper/:name").handler(this::testCaseMapper);
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
     * @param ctx
     */
    public void testCaseMapper2(RoutingContext ctx) {
        logger.info("Testing testcaseMapper2!");
        HttpServerRequest req = ctx.request();

        // Get the contents of the upload
        // TODO: Each of these files needs a unique name since multiple clients might be making concurrent requests
        // FIXME: The tcConfig file and (probably) the mapping.json are small enough to stream into a memory for speed
        UUID id = UUID.randomUUID();
        req.uploadHandler(upload -> {
            String fName = upload.name();
            switch(fName) {
                case "mapping":
                    String path = "/tmp/" + "mapping.json";
                    upload.streamToFileSystem(path);
                    upload.endHandler(v -> {
                        logger.info("mapping.json now fully uploaded");
                        CompletionData data = new CompletionData("mapping", path, id);
                        //this.completions.onNext(data);
                    });
                    break;
                case "jar":
                    String jarpath = "/tmp/" + "jarToCheck.jar";
                    upload.streamToFileSystem(jarpath);
                    upload.endHandler(v -> {
                        logger.info("jar file now fully uploaded");
                        CompletionData data = new CompletionData("jar", jarpath, id);
                        //this.completions.onNext(data);
                    });
                    break;
                case "tcConfig":
                    String configPath = "/tmp/" + "tcConfig.yaml";
                    upload.streamToFileSystem(configPath);
                    upload.endHandler(v -> {
                        logger.info("tcConfig file now fully uploaded");
                        CompletionData data = new CompletionData("jar", configPath, id);
                        //this.completions.onNext(data);
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

    public void testCaseMapper(RoutingContext ctx) {
        logger.info("Just testing!");
        //String test = ctx.getBodyAsJson().getString("hello");
        //logger.info(String.format("Result of test: %s", test));

        // Get the contents of the upload
        String fileName = ctx.request().getParam("name");

        Buffer body = ctx.getBody();
        this.bufferToJar(body, fileName);

        Boolean exists = Paths.get(UPLOAD_DIR, fileName).toFile().exists();
        JsonObject jo = new JsonObject();
        jo.put("result", "congratulations");
        jo.put("file-exists", exists);

        ctx.response().end(jo.encode());
    }


    /**
     * This method will generate an XML xunit file compatible with the XUnit Importer
     *
     * The body of the context will contain the following:
     * - a regular xunit xml file
     * - a JSON dict supplying extra parameters
     * - a mapping.json file
     *
     * @param rc
     */
    public void xunitGenerator(RoutingContext rc) {

    }

    /**
     *
     * @param rc
     */
    public void testcaseImport(RoutingContext rc) {

    }

    /**
     *
     * @param rc
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
