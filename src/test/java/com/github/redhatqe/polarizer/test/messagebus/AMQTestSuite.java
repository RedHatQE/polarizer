package com.github.redhatqe.polarizer.test.messagebus;

import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.data.TestDefinitionType;
import com.github.redhatqe.polarizer.messagebus.CIBusListener;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.reflector.Reflector;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.reactivex.Completable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;

import io.vertx.core.file.CopyOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.TestSuite;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.file.FileSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class AMQTestSuite extends AbstractVerticle {
    private TestSuite suite;
    private Vertx vertx = Vertx.vertx();

    public AMQTestSuite(String name) {
        this.suite = TestSuite.create(name);
    }

    public static TestSuite addTest(TestSuite ts, String name, Handler<TestContext> hdlr) {
        return ts.test(name, hdlr);
    }

    public void addTests(List<Tuple<String, Handler<TestContext>>> tests) {
        tests.forEach(t -> this.suite.test(t.first, t.second));
    }

    public TestDefinition createTestDef(TestDefinition def, List<String> newIds) {
        TestDefinitionType tdt = new TestDefinitionType(def);
        tdt.setTestCaseIDs(newIds);
        return tdt;
    }

    public Completable installFiles(String src, String dest) {
        CopyOptions opts = new CopyOptions();
        opts.setReplaceExisting(true);
        FileSystem fs = this.vertx.fileSystem();
        return fs.rxCopy(src, dest, opts);
    }

    public Handler<TestContext> testReflector() {
        return ctx -> {
            // Setup
            String home = System.getProperty("user.home");
            Path config = Paths.get(home, ".polarize", "test-polarizer-config.yml");
            String cfgDest = "/tmp/test-polarizer-config.yml";
            this.installFiles(config.toString(), cfgDest);
            Path jarPath = Paths.get(home, "/Projects/testpolarize/build/libs/testpolarize-0.1.0-SNAPSHOT.jar");
            String jarDest = "/tmp/testpolarize-0.1.0-SNAPSHOT.jar";
            this.installFiles(jarPath.toString(), jarDest);
            FileHelper.deleteFile("/tmp/mapping.json");

            try {
                Reflector refl = MainReflector.reflect(jarDest, cfgDest, "/tmp/mapping.json");

            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    public Handler<TestContext> testListener() {
        return ctx -> {
            CIBusListener cbl = new CIBusListener();
        };
    }

    public void start() {

    }
}
