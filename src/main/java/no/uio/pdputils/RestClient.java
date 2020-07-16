package no.uio.pdputils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Webservice Rest Client class to request synonyms from semantic reasoner.
 *
 * @author ugurb@ifi.uio.no
 */
public class RestClient {
    private final static String prefix = "RestClient.";
    private static WebClient client = null;
    private final static Logger logger = Logger.getLogger(RestClient.class.getName());
    private static MappingJsonFactory factory = new MappingJsonFactory();
    private static int SUCCESS = 200;

    /**
     * Singleton function for WebClient instance
     *
     * @return
     */
    private static WebClient getSingletonWebClient() {
        if (client == null) {
            client = WebClient.create("http://localhost:8080/sabac-0.0.1-SNAPSHOT/semantic-reasoner");
            client.type(MediaType.WILDCARD).accept(MediaType.WILDCARD);
            HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
            conduit.getClient().setConnectionTimeout(1000 * 3);
            conduit.getClient().setReceiveTimeout(1000 * 3);
        }
        return client;
    }

    /**
     * Invoker function to retrieve synonyms from semantic reasoner with subject role parameter
     *
     * @param role
     * @return
     */
    protected static Document getSemanticReasonerSubjectRoleResult(String role) {
        if (!"".equals(role)) {
            return restInvoker("/query-by-role/" + role);
        }

        return null;
    }

    /**
     * Invoker function to retrieve synonyms from semantic reasoner with access type and subject role parameters
     *
     * @param accessType
     * @param role
     * @return
     */
    protected static Document getSemanticReasonerAccessTypeResult(String accessType, String role) {
        if (!"".equals(accessType) && !"".equals(role))
            return restInvoker("/query-by-access-type/" + accessType.toLowerCase().trim() + "/for/" + role.toLowerCase().trim());

        return null;
    }

    /**
     * @param url
     * @return
     */
    protected static Document restInvoker(String url) {
        Document doc = new Document();
        doc.setContext(false);
        try {
            logger.info(prefix + "restInvoker# Replacing path to url=" + url);
            getSingletonWebClient().replacePath(url);
            logger.info(prefix + " URI:" + getSingletonWebClient().getCurrentURI());
            Response r = getSingletonWebClient().get();
            logger.info(prefix + " r==null? =>" + (r == null));
            logger.info(prefix + " r=>" + r);
            logger.info(prefix + " r.getStatus=>" + r.getStatus());

            if (r != null && r.getStatus() == SUCCESS) {
                JsonParser parser = factory.createJsonParser((InputStream) r.getEntity());
                doc = parser.readValueAs(Document.class);
            }
        } catch (IOException ex) {
            logger.info(prefix + "getSemanticReasonerResult# getLocalizedMessage=" + ex.getLocalizedMessage());
            logger.info(prefix + "getSemanticReasonerResult# getMessage=" + ex.getMessage());
        }
        logger.info(prefix + "getSemanticReasonerResult# returning doc=" + doc);
        return doc;
    }

}
