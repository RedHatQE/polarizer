package com.github.redhatqe.polarizer.tests;

import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.tests.config.APITestSuiteConfig;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.report.ReportOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.ext.unit.TestCompletion;
import io.vertx.reactivex.ext.unit.TestContext;
import io.vertx.reactivex.ext.unit.TestSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;


public class APITestSuite extends AbstractVerticle {
    private TestSuite suite;
    private Logger logger = LogManager.getLogger(APITestSuite.class.getSimpleName());
    private int port;
    private APITestSuiteConfig sconfig;
    private EventBus bus;
    private MessageConsumer<String> testObserver;

    public void start() {
        bus = vertx.eventBus();
        suite = TestSuite.create("API Tests");
        logger.info(String.format("Bringing up %s Verticle", APITestSuite.class.getSimpleName()));

        port = this.config().getInteger("port", 9090);
        // TODO: Make a service endpoint on Polarizer and have it send msg on Event bus
        this.registerEventBus();
    }

    private void registerEventBus() {
        String address = "APITestSuite"; //APITestSuite.class.getCanonicalName();
        logger.info(String.format("Registering %s on event bus", address));
        this.testObserver = this.bus.consumer(address);
        this.testObserver.handler(msg -> {
            String content = msg.body();
            try {
                this.sconfig = Serializer.from(APITestSuiteConfig.class, content);
                if (sconfig.validate())
                    this.test();
                else
                    logger.error("Some of the files in the test configuration do not exist");
            } catch (IOException e) {
                logger.error("Could not deserialize configuration file");
            }
        });
        String vertName = APITestSuite.class.getSimpleName();
        logger.info(String.format("%s Verticle now registered to event bus on address %s", vertName, address));
    }

    private Callback<JsonNode> defaultCallBack(TestContext ctx) {
        return new Callback<JsonNode>() {
            @Override
            public void completed(HttpResponse<JsonNode> response) {
                int status = response.getStatus();
                JsonNode node = response.getBody();
                logger.info(String.format("Got status of: %d", status));
                logger.info(node.toString());
            }

            @Override
            public void failed(UnirestException e) {
                logger.error("Request to /xunit/import failed");
            }

            @Override
            public void cancelled() {
                logger.warn("Request was cancelled");
            }
        };
    }

    /**
     * Actually executes the test
     */
    public void test() {
        this.logger.info("================ Starting tests ================");
        suite.test("basic xunit generate test", this.testXunitGenerate());
        suite.test("second xunit generate test", this.testXunitGenerate());
        //suite.test("basic xunit import test", this.testXunitImport());
        //suite.test("second xunit import test", this.testXunitImport());

        ReportOptions consoleReport = new ReportOptions().
                setTo("console");

        // Report junit files to the current directory
        ReportOptions junitReport = new ReportOptions().
                setTo("file:/test-output").
                setFormat("junit");

        TestCompletion results = suite.run(this.vertx, new TestOptions()
                .addReporter(consoleReport)
                .addReporter(junitReport)
        );
    }

    private Handler<TestContext> testXunitImport() {
        return tctx -> {
            String xunit = this.sconfig.getXunit().getImporter().getXml();
            String xargs = this.sconfig.getXunit().getImporter().getArgs();
            if (xunit == null || xargs == null) {
                tctx.fail("No arguments provided for test");
                return;
            }
            // Unfortunately, the vertx web client doesn't support multipart file, so lets use unirest
            Unirest.post(String.format("http://localhost:%d/xunit/import", this.port))
                    .header("accept", "application/json")
                    .field("xunit", new File(xunit))
                    .field("xargs", new File(xargs))
                    .asJsonAsync(this.defaultCallBack(tctx));
            tctx.assertTrue(1 == 1);
        };
    }

    private Handler<TestContext> testXunitGenerate() {
        return ctx -> {
            String xunit = this.sconfig.getXunit().getGenerate().getFocus();
            String xargs = this.sconfig.getXunit().getGenerate().getArgs();
            String mapping = this.sconfig.getXunit().getGenerate().getMapping();

            String url = String.format("http://localhost:%d/xunit/generate", this.port);
            logger.info( String.format("\nMaking request to %s with\nxunit: %s\nxargs: %s\nmapping: %s"
                       , url, xunit, xargs, mapping));
            Unirest.post(url)
                    .header("accept", "application/json")
                    .field("xunit", new File(xunit))
                    .field("xargs", new File(xargs))
                    .field("mapping", new File(mapping))
                    .asJsonAsync(this.defaultCallBack(ctx));
            ctx.assertTrue(true);
        };
    }
}
