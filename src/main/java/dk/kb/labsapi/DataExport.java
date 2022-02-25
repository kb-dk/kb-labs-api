/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.util.json.JSON;
import dk.kb.util.yaml.YAML;
import dk.kb.webservice.exception.*;
import org.apache.cxf.helpers.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exports images, PDFs, ALTOs and other data.
 */
public class DataExport {
    private static final Logger log = LoggerFactory.getLogger(DataExport.class);
    private static DataExport instance;

    public static final String EDITION_UUID_FIELD = "editionUUID";

    // http://<server>:<port>/ticket-system-service/tickets/
    private final String ticketServiceURL;
    private final String pdfServiceURL;
    private final int maxConnections;
    private final Semaphore connection;

    public static synchronized DataExport getInstance() {
        if (instance == null) {
            instance = new DataExport();
        }
        return instance;
    }

    public DataExport() {
        YAML conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser");
        ticketServiceURL = conf.getString(".ticket.url");
        pdfServiceURL = conf.getString(".download.newspaperpdfurl");
        maxConnections = conf.getInteger(".download.connections", 5);
        connection = new Semaphore(maxConnections == -1 ? Integer.MAX_VALUE : maxConnections, true);
    }

    /**
     * Combines {@link #pdfIdentifierToEditionUUID(String)}, {@link #resolveTicket(String)} and
     * {@link #exportPDF(String)} for the overall goal of getting a PDF belonging to a free form identifier.
     * @param identifier free form identifier: See {@link #pdfIdentifierToEditionUUID(String)} for details.
     * @return the content pf a PDF matching the identifier.
     * @throws ServiceException if the request could not be served.
     */
    public StreamingOutput exportPDF(String identifier) {
        String editionUUID = pdfIdentifierToEditionUUID(identifier);
        String ticket = resolveTicket(editionUUID);
        return exportPDF(editionUUID, ticket);
    }

    /**
     * Transforms a given free-form identifier to proper newspaper editionUUID. Recognized forms are

     * editionId: dagbladetkoebenhavn1851 1855-09-17 001
     * editionUUID: doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611 or uuid:15e8ea25-a194-4f23-bcae-a938c0292611 or 15e8ea25-a194-4f23-bcae-a938c0292611
     * recordID: doms_newspaperCollection:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76-segment_19
     * pageUUID: doms_aviser_page:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76  Note that all of these identifiers will resolve to the same PDF.

     * This might involve a lookup in the underlying Solr. Note that there is no guarantee about availability of the
     * returned editionUUID.
     * @param identifier free form identifier.
     * @return editionUUID in the form {@code 1620bf3b-7801-4a34-b2b9-fd8db9611b76}.
     * @throws ServiceException if the identifier could not be resolved to an editionUUID.
     */
    public String pdfIdentifierToEditionUUID(String identifier) throws ServiceException {
        String match;
        if ((match = getMatch(identifier, EDITION_ID_PATTERN)) != null) {
            // editionID
            return searchEditionUUID(identifier, "editionId:\"" + match + "\"");
        }

        for (Pattern pattern: EDITION_UUID_PATTERNS) {
            if ((match = getMatch(identifier, pattern)) != null) {
                return "doms_aviser_edition:uuid:" + match;
            }
        }

        if ((match = getMatch(identifier, RECORD_ID_PATTERN)) != null) {
            // recordID
            return searchEditionUUID(identifier, "recordID:\"" + match + "\"");
        }

        if ((match = getMatch(identifier, PAGE_UUID_PATTERN)) != null) {
            // PageUUID
            return searchEditionUUID(identifier, "pageUUID:\"" + match + "\"");
        }
        
        throw new InvalidArgumentServiceException(
                "The format of the identifier '" + identifier + "' could not be recognized." +
                "Check the API definition for examples of identifiers");
    }

    /**
     * Resolve editionUUID by search in the Solr responsible for newspaper data.
     * @param identifier free form identifier provided by the caller of the service.
     *                   Used for logging and error messages.
     * @param query      concrete query for Solr.
     * @return the editionUUID for the query.
     * @throws NotFoundServiceException if the query did not match anything or did not match exactly 1 document.
     */
    private String searchEditionUUID(String identifier, String query) {
        Set<String> editionUUIDs =
                SolrExport.getInstance().getSingleField(query, EDITION_UUID_FIELD, 2);
        if (editionUUIDs.isEmpty()) {
            String message = "Unable to resolve editionUUID for identifier '" + identifier +
                             "' with query '" + query + "'";
            log.warn(message);
            throw new NotFoundServiceException(message);
        }
        if (editionUUIDs.size() > 1) {
            String message = "Got " + editionUUIDs.size() + " editionUUIDs for identifier '" + identifier +
                             "' with query '" + query + "' where only 1 is allowed";
            log.warn(message);
            throw new NotFoundServiceException(message);
        }
        String editionUUID = editionUUIDs.iterator().next();
        Matcher matcher = EDITION_UUID_PATTERN_FULL.matcher(editionUUID);
        if (!matcher.matches()) {
            String message = "Got '" + editionUUID + "' as result for identifier '" + identifier +
                             "' with query '" + query + "' but it does not match the expected pattern";
            log.warn(message);
            throw new NotFoundServiceException(message);
        }
        return matcher.group(1);
    }

    

    /**
     * Requests a ticket for the given editionUUID from the ticket service. No special privileges are passed to
     * the ticket service: The only way to get escalated privileges is to call the service from an IP-address that
     * allows them.
     * @param editionUUID in the form {@code doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611}
     * @return the ticket for the editionUUID.
     * @throws InternalServiceException if it was not possible to call the ticket service.
     * @throws ForbiddenServiceException if the IP-address did not grant privileges to download the resource.
     */
    public String resolveTicket(String editionUUID) {
        // 1963: doms_aviser_edition:uuid:69b4d5c4-ac19-4fea-837b-688c7ba0178a
        // 1829: doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611

        // http://<server>:<port>/ticket-system-service/tickets/issueTicket?ipAddress=62.107.214.23&id=doms_aviser_edition:uuid:4d0c2976-0324-4f72-bbcc-edc0030d3503&type=Download&everybody=yes
        URI ticketCall;
        try {
            // TODO: We need the IP of the caller here, to avoid requesting tickets with inhouse privileges
            ticketCall = new URIBuilder(ticketServiceURL + "issueTicket")
                    .addParameter("ipAddress", "0.0.0.0") // TODO: Use forwarded remote IP here
                    .addParameter("id", editionUUID)
                    .addParameter("type", "Download")
                    .addParameter("everybody", "yes")
                    .build();
        } catch (URISyntaxException e) {
            log.error("Internal error: Cannot build URI for ticket service", e);
            throw new InternalServiceException("Unable to construct URI for ticket service");
        }

        log.info("Requesting ticket with '" + ticketCall + "'");

        String ticketResponse;
        try (InputStream in = ticketCall.toURL().openStream()){
            ticketResponse = IOUtils.toString(in);
        } catch (IOException e) {
            log.warn("Exception calling '" + ticketCall + "' for edition '" + editionUUID + "'", e);
            throw new InternalServiceException("Unable to call ticket service for edition '" + editionUUID + "'");
        }

        // {}
        if (ticketResponse.length() < 10) {
            log.info("Unable to request ticket for newspaper edition '" + editionUUID + "'");
            throw new ForbiddenServiceException("Unable to request ticket for newspaper edition '" + editionUUID + "'");
        }

        // {"doms_aviser_edition:uuid:4d0c2976-0324-4f72-bbcc-edc0030d3503":"9ba198ac-b65d-4883-99c5-3cd25eec30c5"}
        try {
            JSONObject json = new JSONObject(ticketResponse);
            return json.getString(editionUUID);
        } catch (Exception e) {
            log.warn("Unable to extract ticket for newspaper edition '" + editionUUID +
                     "' from ticket server response '" + ticketResponse + "'");
            throw new InternalServiceException("Unable to extract ticket for newspaper edition '" + editionUUID +
                                               "' from ticket server response");
        }
    }

    // https://www2.statsbiblioteket.dk/newspaper-pdf/b/a/8/4/ba845c25-d733-48c4-bb8c-6d347fe62f93.pdf?ticket=dfb8388d-d7c4-4ecc-ac83-db34d0339c82&filename=Ki%C3%B8benhavns_Kongelig_alene_priviligerede_Adresse-Contoirs_Efterretninger_(1759-1854)_-_1829-10-31.pdf

    // TODO: Add export through redirect with ticket requested from forwarded IP

    /**
     * Streams a PDF for the given editionUUID from the content server, authorizing assess using the provided ticket.
     * @param editionUUID UUID in the format {@code 1620bf3b-7801-4a34-b2b9-fd8db9611b76}. Prefixes are ignored.
     * @param ticket issued from the ticket server for the specific edition.
     * @return a lazy stream with the PDF data.
     */
    public StreamingOutput exportPDF(String editionUUID, String ticket) {
        Matcher m = EDITION_UUID_SPLITTER.matcher(editionUUID);
        if (!m.matches()) {
            throw new InvalidArgumentServiceException("The editionUUID '" + editionUUID + "' could not be parsed");
        }

        URI resourceCall;
        try {
            resourceCall = new URIBuilder(
                    pdfServiceURL + m.group(2) + "/" + m.group(3) + "/" + m.group(4) + "/" + m.group(5) + "/" +
                    m.group(1) + ".pdf")
                    .addParameter("ticket", ticket)
                    .addParameter("filename", "editionUUID_" + m.group(1) + "_through_labsapi.pdf")
                    .build();
        } catch (URISyntaxException e) {
            log.error("Internal error: Cannot build URI for PDF download", e);
            throw new InternalServiceException("Unable to construct URI for PDF download");
        }

        return output -> {
            try {
                connection.acquire();
                log.info("Proxying PDF download for editionUUID='{}' from URI '{}'", editionUUID, resourceCall);
                try (InputStream in = resourceCall.toURL().openStream()){
                    IOUtils.copyAndCloseInput(in, output);
                } catch (IOException e) {
                    log.warn("Exception piping content from PDF service with URI '" + resourceCall +
                             "' for edition '" + editionUUID + "'", e);
                    throw new InternalServiceException(
                            "Unable to proxy PDF download for edition '" + editionUUID + "'");
                }
            } catch (InterruptedException e) {
                throw new InternalServiceException(
                        "Interrupted while trying to acquire a PDF export connection for edition '" +
                        editionUUID + "'", e);
            } finally {
                connection.release();
            }
        };
    }
    private static final Pattern EDITION_UUID_SPLITTER = Pattern.compile(
            ".*(([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}))");
    
    // doms_aviser_page:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76
    private static final Pattern PAGE_UUID_PATTERN = Pattern.compile(
            "(doms_aviser_page:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    // doms_newspaperCollection:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76-segment_19
    private static final Pattern RECORD_ID_PATTERN = Pattern.compile(
            "(doms_newspaperCollection:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*)");

    // doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611 or uuid:15e8ea25-a194-4f23-bcae-a938c0292611 or 15e8ea25-a194-4f23-bcae-a938c0292611
    private static final Pattern EDITION_UUID_PATTERN_FULL = Pattern.compile(
            "doms_aviser_edition:uuid:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern EDITION_UUID_PATTERN_2 = Pattern.compile(
            "uuid:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern EDITION_UUID_PATTERN_3 = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern[] EDITION_UUID_PATTERNS = new Pattern[] {
            EDITION_UUID_PATTERN_FULL, EDITION_UUID_PATTERN_2, EDITION_UUID_PATTERN_3
    };

    // dagbladetkoebenhavn1851 1855-09-17 001
    private static final Pattern EDITION_ID_PATTERN = Pattern.compile(
            "^([a-z0-9]+.* [0-9]+)$");

    /**
     * Given a str and a pattern, return the first matching group.
     * @param str any String.
     * @param pattern a pattern with at least 1 group.
     * @return the first matching group or null if there are no match.
     */
    private String getMatch(String str, Pattern pattern) {
        Matcher matcher = pattern.matcher(str);
        return matcher.matches() ? matcher.group(1) : null;
    }

}
