package com.github.redhatqe.polarizer.tests;

import io.vertx.core.Handler;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.unit.TestContext;
import io.vertx.reactivex.ext.unit.TestSuite;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



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
    }

    public Handler<TestContext> testTestCaseMapper() {
        return tctx -> {
            // Unfortunately, the vertx web client doesn't support multipart file, so lets use unirest

            tctx.assertTrue(1 == 1);
        };
    }
}
