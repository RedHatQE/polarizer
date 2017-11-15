package com.github.redhatqe.polarizer.jaxb;


import com.github.redhatqe.polarize.exceptions.XMLDescriptonCreationError;
import com.github.redhatqe.polarize.exceptions.XSDValidationError;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;


public interface IJAXBHelper {
    /**
     * Generates an XML file given an object and xsd schema
     *
     * @param t An object whose class is annotated with @XmlRootElement
     * @param xmlpath where to write the generated XML
     * @param xsdpath the URL to the xsd schema to validate against
     * @param <T> The type we are going to marshall into XML
     */
    static <T> void marshaller(T t, File xmlpath, URL xsdpath) {
        try {
            JAXBContext jaxbc = JAXBContext.newInstance(t.getClass());
            Marshaller marshaller = jaxbc.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(t, xmlpath);
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        if (!xmlpath.exists())
            throw new XMLDescriptonCreationError(String.format("Could not find %s", xmlpath.toString()));

        if (xsdpath != null) {
            if (!IJAXBHelper.validateXML(xmlpath, xsdpath))
                throw new XSDValidationError(String.format("Validation failed for %s", xmlpath.toString()));
        }
    }

    /**
     * Generates an Optional of type T given an XML File, and an XSD to validate against
     *
     * @param t Class of t (eg WorkItem.class)
     * @param xmlpath File of xml to unmarshal
     * @param xsdPath File of xsd to validate against
     * @param <T> Type of item that will optionall be contained in return
     * @return an Optional of type T
     */
    static <T> Optional<T> unmarshaller(Class<T> t, File xmlpath, URL xsdPath) {
        XMLInputFactory factory = XMLInputFactory.newFactory();

        Boolean validated = true;
        if (xsdPath != null)
            validated = IJAXBHelper.validateXML(xmlpath, xsdPath);

        if (!validated)
            throw new XSDValidationError(String.format("Validation failed for %s", xmlpath.toString()));

        JAXBElement<T> ret;
        try {
            FileInputStream fis = new FileInputStream(xmlpath);
            XMLEventReader rdr = factory.createXMLEventReader(fis);
            JAXBContext jaxbc = JAXBContext.newInstance(t);

            Unmarshaller um = jaxbc.createUnmarshaller();
            ret = um.unmarshal(rdr, t);
            return Optional.of(ret.getValue());
        } catch (JAXBException | FileNotFoundException | XMLStreamException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Checks that the XML validates against the Schema, and also that all the required fields have valid
     * values
     *
     * @return
     */
    static Boolean validateXML(File xmlpath, URL xsdPath) {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema xsdSchema;
        Boolean returnEmpty = false;
        try {
            xsdSchema = sf.newSchema(xsdPath);
            try {
                javax.xml.validation.Validator v = xsdSchema.newValidator();
                Source xmlSrc = new StreamSource(xmlpath);
                v.validate(xmlSrc);
            } catch (SAXException | IOException e) {
                System.err.println(e.getMessage());
                returnEmpty = true;
            }
        } catch (SAXException e) {
            e.printStackTrace();
            returnEmpty = true;
        }
        return !returnEmpty;
    }

    public URL getXSDFromResource(Class<?> t);
}
