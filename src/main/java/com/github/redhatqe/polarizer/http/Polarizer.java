package com.github.redhatqe.polarizer.http;

import io.reactivex.disposables.Disposable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


public class Polarizer extends AbstractVerticle {

    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String UPLOAD_DIR = "/tmp";
    private Disposable serverDisp;

    public void start() {
        EventBus bus = vertx.eventBus();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create()
                .setBodyLimit(209715200L)                 // Max Jar size is 200MB
                .setDeleteUploadedFilesOnEnd(false)       // FIXME: for testing only.  In Prod set to true
                .setUploadsDirectory(UPLOAD_DIR));

        router.post("/testcase/mapper/:name").handler(this::testCaseMapper);
        router.post("/xunit/generator").handler(this::xunitGenerator);
        router.post("/testcase/import").handler(this::testcaseImport);


        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        serverDisp = server.requestHandler(router::accept)
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
        try {
            ByteBuffer buff = ByteBuffer.allocate(body.length());
            FileChannel fc = FileChannel.open(Paths.get(UPLOAD_DIR, name), StandardOpenOption.CREATE_NEW);
            for(int i = 0; i < body.length(); i++) {
                buff.put(body.getByte(i));
            }
            buff.flip();

            while(buff.hasRemaining()) {
                fc.write(buff);
            }
            fc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
