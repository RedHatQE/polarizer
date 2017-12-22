package com.github.redhatqe.polarizer;

import com.github.redhatqe.polarizer.http.Polarizer;
import com.github.redhatqe.polarizer.http.config.PolarizerVertConfig;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.tests.APITestSuite;
import com.github.redhatqe.polarizer.tests.config.APITestSuiteConfig;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class MainVerticle extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String POLARIZER_VERT = Polarizer.class.getCanonicalName();
    public static final String POLARIZER_ENV = "POLARIZER_MAIN_CONFIG";
    public static final String POLARIZER_PROP = "polarizer.main.config";
    public static final String TEST_VERT = APITestSuite.class.getCanonicalName();
    public static final String TEST_ENV = "POLARIZER_TEST_CONFIG";
    public static final String TEST_PROP = "polarizer.test.config";

    private Disposable dep;

    public void start() throws IOException {
        VertxOptions opts = new VertxOptions();
        opts.setBlockedThreadCheckInterval(120000);
        this.vertx = Vertx.vertx(opts);
        DeploymentOptions pOpts = this.setupConfig(PolarizerVertConfig.class, POLARIZER_ENV, POLARIZER_PROP);
        DeploymentOptions tOpts = this.setupConfig(APITestSuiteConfig.class, TEST_ENV, TEST_PROP);

        Single<String> polarizerDeployer = vertx.rxDeployVerticle(POLARIZER_VERT, pOpts);
        dep = polarizerDeployer.subscribe(succ -> {
                    // Start the APITestSuite verticle once the Polarizer verticle is running
                    Single<String> deployed = vertx.rxDeployVerticle(TEST_VERT, tOpts);
                    deployed.subscribe(next -> logger.info("APITestSuite Verticle is now deployed"),
                            err -> logger.error(err.getMessage()));
                    logger.info("Polarizer was deployed");
            },
            err -> logger.error("Failed to deploy Polarizer\n" + err.getMessage()));
    }

    public void stop() {
        this.dep.dispose();
    }

    private <T> DeploymentOptions setupConfig(Class<T> cls, String env, String prop) throws IOException {
        DeploymentOptions opts = new DeploymentOptions();
        String envPath = System.getenv(env);
        String path = System.getProperty(prop);
        String filePath = envPath == null ? "" : path == null ? "" : path;
        File fpath = new File(filePath);
        T pCfg;
        if (fpath.exists()) {
            pCfg = Serializer.from(cls, fpath);
            opts.setConfig(JsonObject.mapFrom(pCfg));
        }

        return opts;
    }
}
