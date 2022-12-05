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
import dk.kb.util.webservice.exception.InternalServiceException;
import dk.kb.util.webservice.exception.InvalidArgumentServiceException;
import dk.kb.util.xml.XMLStepper;
import dk.kb.util.yaml.YAML;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.GroupParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exports data from the Summarise setup underneath Mediestream.
 */
public class SummariseExport {
    private static final Logger log = LoggerFactory.getLogger(SummariseExport.class);
    private static SummariseExport instance;

    final static Pattern UUID_PATTERN = Pattern.compile(
            ".*([a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}).*");
    final static XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();
    static {
        XML_FACTORY.setProperty(XMLInputFactory.IS_COALESCING, true); // Return all text extractions in full
    }


    // http://example.org:56708/aviser/
    private final String summariseService;

    public static SummariseExport getInstance() {
        if (instance == null) {
            instance = new SummariseExport();
        }
        return instance;
    }

    private SummariseExport() {
        YAML conf;
        try {
            conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser.summarise");
        } catch (Exception e) {
            log.error("The configuration sub map '.labsapi.aviser.summarise' was not defined");
            summariseService = null;
            return;
        }
        summariseService = conf.getString(".url") + (conf.getString(".url").endsWith("/") ? "" : "/");
        log.info("Created SummariseExport with url '{}'", summariseService);
    }

    /**
     * Parse the ID as either a direct recordID, a UUID or a Mediestream URL and resolve the concrete recordID
     * using the {@link SolrExport}, then retrieve the ALTO XML from Summarise storage and return it unmodified.
     * @param id a Mediestream URL to a newspaper page or an article UUID. Leniently parsed.
     * @return the ALTO-XML for the newspaper page matching the ID.
     */
    public String getALTO(String id) {
        if (summariseService == null) {
            throw new InternalServiceException("ALTO delivery service has not been configured, sorry");
        }

        String uuid = extractUUID(id);
        String recordID = uuidToRecordID(id, uuid);

        log.debug("Retrieving SOAP XML for recordID '{}'", recordID);

        URL url;
        try {
            // http://example.org:56708/aviser/storage/services/StorageWS?method=getLegacyRecord&id=doms_newspaperCollection:uuid:86c5934f-6bb0-4325-b56b-e9b112990ec2
            url = new URIBuilder(summariseService + "storage/services/StorageWS").
                    addParameter("method", "getLegacyRecord").
                    addParameter("id", recordID).build().toURL();
        } catch (Exception e) {
            log.warn("Unable to construct SOAP URL from ALTO id '" + id + "' resolved to record '" + recordID + "'", e);
            throw new IllegalArgumentException("Unable to construct SOAP URL from ALTO id '" + id + "'");
        }

        String soapXML;
        try {
            log.debug("Requesting '{}'", url);
            soapXML = IOUtils.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Unable to retrieve ALTO XML for id '" + id + "' resolved to recordID '" + recordID +
                     "' with request URL '" + url + "'", e);
            throw new RuntimeException("Unable to retrieve ALTO XML for ID '" + id + "'");
        }

        String alto = extractALTOFromFedoraXML(extractRecord(soapXML, uuid), uuid);
        log.debug("Resolved ALTO request for id '{}' to a {} character ALTO XML", id, alto.length());
        return alto;
    }

    /**
     * Extract an UUID from the given id using pattern matching. Only requirement for matching is that a substring
     * follows the UUID 8-4-4-4-12 hex pattern, e.g. {@code 1620bf3b-7801-4a34-b2b9-fd8db9611b76}.
     * @param id an ID containing an UUID.
     * @return the UUID part of the ID.
     * @throws InvalidArgumentServiceException if no UUID could be extracted.
     */
    public static String extractUUID(String id) {
        if (id == null) {
            throw new InvalidArgumentServiceException("No ID provided");
        }
        Matcher matcher = UUID_PATTERN.matcher(id);
        if (!matcher.matches()) {
            throw new InvalidArgumentServiceException(
                    "Unable to extract UUID from '" + id + "'. Expected 8-4-4-4-12 hex pattern, e.g. " +
                    "1620bf3b-7801-4a34-b2b9-fd8db9611b76");
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Takes a SOAP envelope and extract the content.
     */
    // <?xml version="1.0" encoding="UTF-8"?>
    // <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    // <soapenv:Body>
    // <getLegacyRecordResponse xmlns="">
    // <ns1:getLegacyRecordReturn xmlns:ns1="http://statsbiblioteket.dk/summa/storage">&lt;dobundle:digitalObjectBundle xmlns...
    private String extractRecord(String soapXML, String uuid) {
        try {
            return XMLStepper.evaluateFakeXPath(soapXML, "Envelope/Body/getLegacyRecordResponse/getLegacyRecordReturn");
        } catch (XMLStreamException e) {
            log.warn("Unable to parse SOAP response for uuid '" + uuid + "'", e);
            throw new RuntimeException("Unable to parse SOAP XML response");
        }
    }

    /**
     * Takes a Fedora XML and extracts the content of the first {@code ALTO} datastream.
     */
    //  <dobundle:digitalObjectBundle xmlns:dobundle="http://doms.statsbiblioteket.dk/types/digitalobjectbundle/default/0/1/#">
    //    <foxml:digitalObject xmlns:foxml="info:fedora/fedora-system:def/foxml#" xmlns:xsi="http://www.w3.org/2001...
    //     ...
    //      <foxml:datastream CONTROL_GROUP="M" ID="ALTO" STATE="A" VERSIONABLE="true">
    //        <foxml:datastreamVersion ALT_IDS="B400026957719-RT1/400026957719-01/1857-04-11-01/dannevirke1838-1857-04-11-01-0173B.alto.xml" CREATED="2016-04-09T20:27:54.248Z" ID="ALTO.0" LABEL="" MIMETYPE="text/xml" SIZE="259901">
    //           <foxml:xmlContent>
    //                  <alto xmlns="http://www.loc.gov/standards/alto/ns-v2#" xsi:schemaLocation="http://www.loc.gov/standards/alto/ns-v2# alto-v2.0.xsd">
    private String extractALTOFromFedoraXML(String fedoraXML, String uuid) {
        final String xPath = "digitalObjectBundle/digitalObject/datastream[@ID='ALTO']/datastreamVersion/xmlContent/alto";
        //final String xPath = "digitalObjectBundle/digitalObject/datastream/datastreamVersion/xmlContent";
        try {
            XMLStreamReader xmlReader = XMLStepper.jumpToNextFakeXPath(fedoraXML, xPath);
            if (xmlReader == null) {
                String message = "Got response for record but could not locate ALTO for uuid '" + uuid + '"';
                log.warn(message);
                throw new InternalServiceException(message);
            }
            return XMLStepper.getSubXML(xmlReader, true);
        } catch (XMLStreamException e) {
            log.warn("Unable to extract ALTO from record for uuid '" + uuid + "'", e);
            throw new InternalServiceException("Unable to extract ALTO from record for uuid '" + uuid + "'");
        }
    }

    // Returns a recordID or throws an exception
    private String uuidToRecordID(String id, String uuid) {
        String query = String.format(
                Locale.ROOT,
                // doms_newspaperCollection:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76-segment_19
                "recordID:\"doms_newspaperCollection:uuid:%1$s\" OR \n" +
                // https://www2.statsbiblioteket.dk/mediestream/avis/record/doms_aviser_page:uuid:a9990f12-e9f0-4b1e-becc-e0d4bf514586/query/heste)
                "pageUUID:\"doms_aviser_page:uuid:%1$s\"",
                uuid);
        String filter = "recordBase:doms_aviser_page AND py:[* TO 1880]";

        log.debug("Verifying validity and resolving recordID from given UUID '{}' with query='{}'",
                  uuid, query);

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFilterQueries(filter); // Probably overwritten by the SolrClient
        solrQuery.setFields("recordID");
        solrQuery.setRows(1);
        solrQuery.setFacet(false);
        solrQuery.setHighlight(false);
        solrQuery.set(GroupParams.GROUP, false);
        QueryResponse response;
        try {
            response = SolrExport.getInstance().callSolr(solrQuery);
        } catch (Exception e) {
            String message = "Error calling Solr for ALTO id '" + id + "' resolved to UUID '" + uuid + "'";
            log.warn(message, e);
            throw new RuntimeException(message);
        }
        if (response.getResults().isEmpty()) {
            log.info("uuidToRecordID: No results for query '{}' constructed from input id '{}'", query, id);
            if (SolrExport.getInstance().countHits(true, query) != 0) {
                throw new IllegalArgumentException(
                        "Unable to export ALTO id '" + id + "'. The material is not old enough to be out of copyright");
            }
            throw new IllegalArgumentException("Unable to resolve ALTO id '" + id + "'");
        }
        Object oID = response.getResults().get(0).getFieldValue("recordID");
        if (oID == null) {
            log.error("Could not get recordID from search result for id '{}'", id);
            throw new RuntimeException("Internal server error: Unable to resolve recordID");
        }
        // Ideally the filter should work and this would be the page
        // doms_newspaperCollection:uuid:7206e433-48d1-4c2a-af3f-4239ef1b62cc
        // But implicit filtering might return recordBase:doms_aviser and that has the form
        // doms_newspaperCollection:uuid:7206e433-48d1-4c2a-af3f-4239ef1b62cc-segment_19
        Matcher pageIDMatcher = UUID_PATTERN.matcher(oID.toString());
        if (!pageIDMatcher.matches()) {
            log.warn("Unable to extract UUID from recordID '{}'", oID);
            throw new RuntimeException("Unable to extract UUID from recordID '" + oID + "'");
        }

        return "doms_newspaperCollection:uuid:" + pageIDMatcher.group(1);
    }
}
