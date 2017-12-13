package com.github.redhatqe.polarizer.tests;

import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.tests.config.APITestSuiteConfig;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.vertx.core.Handler;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.unit.TestContext;
import io.vertx.reactivex.ext.unit.TestSuite;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;


public class APITestSuite extends AbstractVerticle {
    private TestSuite suite;
    private Vertx vertx;
    private Logger logger = LogManager.getLogger(APITestSuite.class.getSimpleName());
    private int port;
    private WebClientOptions cOpts;
    private WebClient client;
    private APITestSuiteConfig sconfig;

    public void start() {
        suite = TestSuite.create("API Tests");

        port = this.config().getInteger("port", 9090);
        logger.info(String.format("Running on port %d", this.port));

        //cOpts = new WebClientOptions().setKeepAlive(true);
        //client = WebClient.create(this.vertx, this.cOpts);

        // TODO: Make a service endpoint on Polarizer and have it send msg on Event bus
        // so that we can run this test programmatically
        String cfgPath = System.getenv("POLARIZER_TESTING");
        try {
            sconfig = Serializer.from(APITestSuiteConfig.class, new File(cfgPath));
            if (sconfig.validate())
                this.test();
            else
                logger.error("Some of the files in the test configuration do not exist");
        } catch (IOException e) {
            logger.error("Could not find configuration file.  Remember to set config path to POLARIZER_TESTING");
        }
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
        //suite.test("basic xunit generate test", this.testXunitGenerate());
        suite.test("basic xunit import test", this.testXunitImport());
        //suite.test("second xunit generate test", this.testXunitGenerate());
        suite.run();
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
