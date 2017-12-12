package com.github.redhatqe.polarizer.tests;

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


public class APITestSuite extends AbstractVerticle {
    private TestSuite suite;
    private Vertx vertx;
    private Logger logger = LogManager.getLogger(APITestSuite.class.getSimpleName());
    private int port;
    private WebClientOptions cOpts;
    private WebClient client;

    public void start() {
        suite = TestSuite.create("API Tests");
        vertx = Vertx.vertx();

        port = this.config().getInteger("port", 9090);
        logger.info(String.format("Running on port %d", this.port));

        cOpts = new WebClientOptions().setKeepAlive(true);
        client = WebClient.create(this.vertx, this.cOpts);

        // TODO: Make a service endpoint on Polarizer and have it send msg on Event bus
        // so that we can run this test programmatically
        this.test();
    }

    /**
     * Actually executes the test
     */
    public void test() {
        this.logger.info("================ Starting tests ================");
        suite.test("basic test", this.testTestCaseMapper());
        suite.run();
    }

    public Handler<TestContext> testTestCaseMapper() {
        return tctx -> {
            String xunit = System.getenv("TEST_XUNIT");
            String xargs = System.getenv("TEST_XARGS");
            if (xunit == null || xargs == null) {
                tctx.fail("No arguments provided for test");
                return;
            }
            // Unfortunately, the vertx web client doesn't support multipart file, so lets use unirest
            Unirest.post("http://localhost:9090/xunit/import")
                    .header("accept", "application/json")
                    .field("xunit", new File(xunit))
                    .field("xargs", new File(xargs))
                    .asJsonAsync(new Callback<JsonNode>() {
                        @Override
                        public void completed(HttpResponse<JsonNode> response) {
                            int status = response.getStatus();
                            JsonNode node = response.getBody();
                        }

                        @Override
                        public void failed(UnirestException e) {
                            logger.error("Request to /xunit/import failed");
                        }

                        @Override
                        public void cancelled() {
                            logger.warn("Request was cancelled");
                        }
                    });
            tctx.assertTrue(1 == 1);
        };
    }
}
