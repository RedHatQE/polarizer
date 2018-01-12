package com.github.redhatqe.polarizer;

import com.github.redhatqe.polarizer.messagebus.CIBusListener;
import com.github.redhatqe.polarizer.messagebus.MessageResult;
import com.github.redhatqe.polarizer.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarizer.reporter.jaxb.JAXBHelper;
import com.github.redhatqe.polarizer.utils.IFileHelper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.stream.Collectors;


public class ImporterRequest {
    private static Logger logger = LoggerFactory.getLogger(ImporterRequest.class);

    /**
     * Marshalls t of Type T into xml file and uses this generated xml for an importer request
     *
     * @param t The object to be marshalled to xml
     * @param tclass class type to marshall to
     * @param url the URL to send import request to
     * @param xml path for where to store the XML that will be sent to import request
     * @param <T> type of t
     * @return response from sending request
     */
    public static <T> CloseableHttpResponse post(T t, Class<T> tclass, String url, String xml, String user, String pw) {
        JAXBHelper jaxb = new JAXBHelper();
        File importerFile = new File(xml);
        IFileHelper.makeDirs(importerFile.toPath());
        IJAXBHelper.marshaller(t, importerFile, jaxb.getXSDFromResource(tclass));

        return ImporterRequest.post(url, importerFile, user, pw);
    }


    /**
     * Uploads a file to a server through a POST call in multipart_form_data style
     *
     * This style of post is used by the XUnit and Testcase importer, the only difference is the endpoint in the URL.
     * Note that the http response does not hold the data from the request.  Instead, the response of the request is
     * actually sent through the CI Message Bus.  See CIBusListener class
     *
     * @param url url endpoint
     * @param importerFile file to upload
     * @param user username authorized to use service
     * @param pw password for user
     * @return http response
     */
    public static CloseableHttpResponse post(String url, File importerFile , String user, String pw) {
        CloseableHttpResponse response = null;
        if (!importerFile.exists()) {
            logger.error(String.format("%s did not exist", importerFile.toString()));
            return null;
        }
        ImporterRequest.logger.info(String.format("Sending %s to importer:\n", importerFile.toString()));

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, pw);
        provider.setCredentials(AuthScope.ANY, credentials);

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .setRedirectStrategy(new LaxRedirectStrategy());

        // FIXME: This should probably go into a helper class since the XUnitService is going to need this too
        try {
            URI polarion = null;
            try {
                polarion = new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            CloseableHttpClient httpClient;
            if (polarion != null && polarion.getScheme().equals("https")) {
                // setup a Trust Strategy that allows all certificates.
                SSLContext sslContext = null;
                try {
                    sslContext = new SSLContextBuilder().loadTrustMaterial(null, (arg0, arg1) -> true).build();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (KeyManagementException e) {
                    ImporterRequest.logger.error("KeyManagement error");
                    e.printStackTrace();
                } catch (KeyStoreException e) {
                    ImporterRequest.logger.error("KeyStore error");
                    e.printStackTrace();
                }
                builder.setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(provider)
                        .setRedirectStrategy(new LaxRedirectStrategy());
            }

            httpClient = builder.build();
            HttpPost postMethod = new HttpPost(url);

            MultipartEntityBuilder body = MultipartEntityBuilder.create();
            body.addBinaryBody("file", importerFile);
            body.setContentType(ContentType.MULTIPART_FORM_DATA);
            HttpEntity bodyEntity = body.build();
            postMethod.setEntity(bodyEntity);
            response = httpClient.execute(postMethod);
            ImporterRequest.logger.info(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response != null)
            System.out.println(ImporterRequest.getBody(response));
        return response;
    }

    private static String getBody(HttpResponse response) {
        HttpEntity entity = response.getEntity();
        String body = "";
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            body = bfr.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return body;
    }

    private static Optional<File> getBody(HttpResponse response, String path) {
        if (response.getStatusLine().getStatusCode() != 200) {
            logger.error("Status was %s", response.getStatusLine().toString());
            return Optional.empty();
        }

        Path file = null;
        try {
            String body = getBody(response);
            System.out.println(body);

            file = Paths.get(path);
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                writer.write(body);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (file == null)
            return Optional.empty();
        else
            return Optional.of(file.toFile());
    }

    public static <T> Optional<MessageResult<T>>
    sendImportByTap( CIBusListener<T> cbl
                   , String url
                   , String user
                   , String pw
                   , File reportPath
                   , String selector
                   , String address) {
        Optional<Connection> conn = cbl.tapIntoMessageBus(selector, cbl.createListener(cbl.messageParser()), address);
        MessageResult<T> msg;

        logger.info("Making import request as user: " + user);
        CloseableHttpResponse resp = ImporterRequest.post(url, reportPath, user, pw);
        // TODO: Get the body of the message which should contain a job ID.  We can use the Job ID to track it in the
        // new Polarion Queue browser
        if (resp != null)
            logger.info(resp.toString());
        else
            return Optional.empty();

        if (resp.getStatusLine().getStatusCode() != 200) {
            logger.error("Problem sending POST request");
            msg = new MessageResult<>(null, MessageResult.Status.FAILED);
            msg.errorDetails = resp.getStatusLine().getReasonPhrase();
        }
        else {
            String body = ImporterRequest.getBody(resp);
            logger.info(body);

            // FIXME: Do we really want to spin here?  We can be notified when the response message comes in another
            // way.  Make the message listener a service that runs, and it will do something (eg send email, update
            // database, update browser, etc) when message arrives or times out.  If it times out, make a request to the
            // queue browser to see if the request is stuck in the queue
            cbl.listenUntil(1);
        }

        conn.ifPresent(c -> {
            try {
                c.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });

        if (!cbl.messages.isEmpty()) {
            msg = cbl.messages.remove();
            logger.info(String.format("The message response status is: %s", msg.getStatus().name()));
        }
        else
            msg = new MessageResult<>(null, MessageResult.Status.NO_MESSAGE);
        return Optional.of(msg);
    }

    /**
     * A Basic get function that can be used to download something from a URL on an https site
     *
     * @param url url of the location
     * @param user user to authenticate as
     * @param pw password for given user
     * @param path path to store body of file
     * @return an Optional File to stored data
     */
    public static Optional<File> get(String url, String user, String pw, String path) {
        CloseableHttpResponse response;
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, pw);
        provider.setCredentials(AuthScope.ANY, credentials);
        Optional<File> maybeFile = Optional.empty();

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .setRedirectStrategy(new LaxRedirectStrategy());

        try {
            URI server = null;
            try {
                server = new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            CloseableHttpClient httpClient;
            if(server != null && server.getScheme().equals("https")) {
                // setup a Trust Strategy that allows all certificates.
                SSLContext sslContext = null;
                try {
                    sslContext = new SSLContextBuilder().loadTrustMaterial(null, (arg0, arg1) -> true).build();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (KeyManagementException e) {
                    ImporterRequest.logger.error("KeyManagement error");
                    e.printStackTrace();
                } catch (KeyStoreException e) {
                    ImporterRequest.logger.error("KeyStore error");
                    e.printStackTrace();
                }
                builder.setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(provider)
                        .setRedirectStrategy(new LaxRedirectStrategy());
            }

            httpClient = builder.build();
            HttpGet getMethod = new HttpGet(url);
            response = httpClient.execute(getMethod);
            ImporterRequest.logger.info(response.toString());

            maybeFile = ImporterRequest.getBody(response, path);
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return maybeFile;
    }

    /**
     * A simple method to download a file from a URL (no https, use get for https site)
     *
     * @param url url to get file
     * @param output path for where to store download
     * @return
     */
    public static File download(String url, String output) {
        File file = new File(output);
        try {
            URL rUrl = new URL(url);
            ReadableByteChannel rbc = Channels.newChannel(rUrl.openStream());
            FileOutputStream fos = new FileOutputStream(file);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static void main(String[] args) {
        // Get a file from jenkins
        String url = args[3];

        String user = args[0];
        String pw = args[1];
        String path = args[2];
        Optional<File> file = ImporterRequest.get(url, user, pw, path);
        if (file.isPresent()) {
            Path fpath = file.get().toPath();
            try {
                BufferedReader bfr = Files.newBufferedReader(fpath);
                String body = bfr.lines().collect(Collectors.joining("\n"));
                System.out.println(body);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
