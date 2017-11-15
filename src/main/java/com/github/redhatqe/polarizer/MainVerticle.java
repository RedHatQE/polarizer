package com.github.redhatqe.polarizer;

import com.github.redhatqe.polarizer.http.Polarizer;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainVerticle extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    private Disposable dep;

    public void start() {
        Future<String> future = Future.future();

        Single<String> polarizerDeployer = vertx.rxDeployVerticle("com.github.redhatqe.polarizer.http.Polarizer");
        dep = polarizerDeployer.subscribe(succ -> logger.info("Polarizer was deployed"),
                                          err -> logger.error("Failed to deploy Polarizer\n" + err.getMessage()));
    }
}
