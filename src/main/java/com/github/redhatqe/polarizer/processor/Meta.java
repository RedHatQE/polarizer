package com.github.redhatqe.polarizer.processor;

import com.github.redhatqe.polarize.metadata.QualifiedName;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.data.MetaType;
import com.github.redhatqe.polarizer.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarizer.reporter.jaxb.JAXBHelper;
import com.github.redhatqe.polarizer.reporter.importer.testcase.Parameter;
import com.github.redhatqe.polarizer.reporter.importer.testcase.Testcase;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Contains the fully qualified name of a @TestDefinition decorated method
 */
public class Meta<T> {
    public String packName;
    public String className;
    public String methName;
    public String qualifiedName;
    public String project;
    public T annotation;
    public List<Parameter> params = null;
    public String polarionID = "";
    public static final Logger logger = LoggerFactory.getLogger(Meta.class.getSimpleName());
    public Boolean dirty = false;

    public Meta() {

    }

    public static <A> Meta<A> create(String qual, String meth, String cls, String pack, String proj, String id,
                                     List<Parameter> params, A ann) {
        Meta<A> meta = new Meta<>();
        meta.qualifiedName = qual;
        meta.methName = meth;
        meta.className = cls;
        meta.packName = pack;
        meta.project = proj;
        meta.polarionID = id;
        meta.annotation = ann;
        meta.params = params;
        return meta;
    }

    public static MetaType makeMeta(Meta<TestDefinition> meta) {
        return new MetaType(meta);
    }

    /**
     * Given a qualified name like com.github.redhat.qe.polarize.Foo.barMethod returns package, class and method
     *
     * @param path
     * @return
     */
    public static QualifiedName getClassMethodFromDottedString(String path) throws Exception {
        QualifiedName qual = new QualifiedName();
        String[] dots = path.split("\\.");
        if (dots.length < 3)
            throw new Exception(String.format("%s not a valid dotted name. Must have package.class.methodname", path));

        qual.methName = dots[dots.length - 1];
        qual.className = dots[dots.length - 2];

        String[] pkg = new String[dots.length - 2];
        System.arraycopy(dots, 0, pkg, 0, pkg.length);
        qual.packName = Arrays.stream(pkg).reduce("", (acc, n) -> acc + "." + n).substring(1);
        return qual;
    }

    /**
     * FIXME: this assumes the path is based on the class.method name but ignores if xmlPath is used in annotation
     *
     * Creates an xml path for a test method.  The path generated will take the form:</br>
     * /{base}/{projID}/{qualname}
     * </p>
     * Normally, the base will come from the configuration file, extra will come from the class.methodName, and project will
     * be determined based on some other factor like the project in a Meta object.
     *
     * @param base From the configuration file \<testcases-xml path={}\>
     * @param extra a string representing an extra set of paths concatenated to base
     * @param projID the project
     * @return
     */
    public static <T> Optional<Path> getXmlPath(String base, String extra, String projID) {
        Path path = null;
        try {
            QualifiedName qual = getClassMethodFromDottedString(extra);
            Meta<T> meta = new Meta<>();
            meta.project = projID;
            meta.className = qual.className;
            meta.packName = qual.packName;
            meta.methName = qual.methName;
            path = FileHelper.makeXmlPath(base, meta);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (path == null)
            return Optional.empty();
        else
            return Optional.of(path);
    }

    /**
     *
     * @return
     */
    public Optional<String> getPolarionIDFromTestcase() {
        String id = this.polarionID;
        if (id.equals(""))
            return Optional.empty();
        return Optional.of(id);
    }

    /**
     * Unmarshalls an Optional of type T from the given Meta object
     *
     * From the data contained in the Meta object, function looks for the XML description file and unmarshalls it to
     * the class given by class t.
     *
     * @param t class type
     * @param tcPath the testcase path (eg from reporter.properties)
     * @return Optionally a type of T if possible
     */
    public <T1> Optional<T1> getTypeFromMeta(Class<T1> t, String tcPath) {
        //TODO: Check for XML Desc file for TestDefinition
        Path path = FileHelper.makeXmlPath(tcPath, this);
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();

        Meta.logger.debug("Description file exists: " + xmlDesc.toString());
        Optional<T1> witem;
        JAXBHelper jaxb = new JAXBHelper();
        witem = IJAXBHelper.unmarshaller(t, xmlDesc, jaxb.getXSDFromResource(t));
        return witem;
    }

    /**
     * Unmarshalls Testcase from XML pointed at in meta, and gets the Polarion ID
     *
     * *Note* we are returning a Tuple now to avoid extra calls to unmarshall from the XML
     *
     * @param tcPath path to the testcases
     * @return Optionally the String of the Polarion ID and the unmarshalled version of the Testcase
     */
    public Optional<Tuple<String, Testcase>> getPolarionIDFromXML(String tcPath) {
        Optional<Testcase> tc = this.getTypeFromMeta(Testcase.class, tcPath);
        Tuple<String, Testcase> res = new Tuple<>();

        if (!tc.isPresent()) {
            Meta.logger.info(String.format("Unmarshalling failed for %s.  No Testcase present...", tcPath));
            return Optional.empty();
        }
        else if (tc.get().getId() == null || tc.get().getId().equals("")) {
            Meta.logger.info(String.format("For %s: No id attribute for <testcase> element", tc.get().getTitle()));

            res.first = "";
            res.second = tc.get();
            return Optional.of(res);
        }
        Testcase tcase = tc.get();
        Meta.logger.debug("Polarion ID for testcase " + tcase.getTitle() + " is " + tcase.getId());
        res.first = tcase.getId();
        res.second = tcase;
        return Optional.of(res);
    }

    /**
     * Returns possible file location to the XML description file based on a Meta type and a project
     *
     * Uses the information from the meta type and project to know where to find XML description file
     *
     * @param tcPath The testcase path (eg from reporter.properties)
     * @return An Optional<File> if the xml exists
     */
    public Optional<File> getFileFromMeta(String tcPath) {
        Path path = FileHelper.makeXmlPath(tcPath, this);
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();
        return Optional.of(xmlDesc);
    }
}
