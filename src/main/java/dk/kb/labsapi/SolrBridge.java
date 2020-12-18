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
import dk.kb.webservice.exception.InternalServiceException;
import joptsimple.internal.Strings;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

/**
 * Handles Solr comminication.
 */
public class SolrBridge {
    private static Log log = LogFactory.getLog(SolrBridge.class);

    final static SimpleDateFormat HUMAN_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
    private static SolrClient solrClient;
    private static int pageSize = 500;
    private static String exportSort = null;

    public enum STRUCTURE { comments, header, content ;
        public static Set<STRUCTURE> DEFAULT = new HashSet<>(Arrays.asList(comments, header, content)) ;
        public static Set<STRUCTURE> valueOf(List<String> vals) {
            return vals == null || vals.isEmpty() ?
                    DEFAULT :
                    vals.stream().map(STRUCTURE::valueOf).collect(Collectors.toSet());
        }
    }

    private static ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        int threadID = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SolrThread_" + threadID++);
            t.setDaemon(true);
            return t;
        }
    });

    private static SolrClient getClient() {
        if (solrClient == null) {
            String solrURL = ServiceConfig.getConfig().getString(".labsapi.aviser.solr.url");
            String collection = ServiceConfig.getConfig().getString(".labsapi.aviser.solr.collection");
            String fullURL = solrURL + (solrURL.endsWith("/") ? "" : "/") + collection;
            String filter = ServiceConfig.getConfig().getString(".labsapi.aviser.solr.filter", null);

            ModifiableSolrParams baseParams = new ModifiableSolrParams();
            baseParams.set(HighlightParams.HIGHLIGHT, false);
            baseParams.set(FacetParams.FACET, false);
            baseParams.set(GroupParams.GROUP, false);
            if (filter != null) {
                baseParams.set(CommonParams.FQ, filter);
            }
            pageSize = ServiceConfig.getConfig().getInteger(".labsapi.aviser.export.solr.pagesize");
            exportSort = ServiceConfig.getConfig().getString(".labsapi.aviser.export.solr.sort");

            log.info("Creating SolrClient(" + fullURL + ") with filter='" + filter + "'");
            solrClient = new HttpSolrClient.Builder(fullURL).withInvariantParams(baseParams).build();
        }
        return solrClient;
    }

    public static StreamingOutput export(String query, Collection<String> fields, Set<STRUCTURE> structure) {
        getClient(); // Placeholder to set up the service, needed until a proper implementation
        if (exportSort == null) {
            throw new InternalServiceException(
                    "Error: Unable to export: No export sort (labsapi.aviser.export.solr.sort) specified in config");
        }

        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString(pageSize),
                CommonParams.SORT, exportSort,
                 CommonParams.FL, Strings.join(fields, ",")); // TODO: If link is present, retrieve recordID

        return streamResponse(request, query, fields, structure);
    }

    public static long countHits(String query) {
        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString(0));
        try {
            QueryResponse response = getClient().query(request);
            return response.getResults().getNumFound();
        } catch (Exception e) {
            log.warn("Exception calling Solr for countHits(" + query + ")", e);
            throw new InternalServiceException(
                    "Internal error counting hits for query '" + query + "': " + e.getMessage());
        }
    }

    private static StreamingOutput streamResponse(
            SolrParams request, String query, Collection<String> fields, Set<STRUCTURE> structure) {
        return output -> {
            try (OutputStreamWriter os = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                if (structure.contains(STRUCTURE.comments)) {
                    os.write("# kb-labs-api export of Mediestream aviser data"+ "\n");
                    os.write("# query: " + query.replace("\n", "\n") + "\n");
                    os.write("# fields: " + fields.toString() + "\n");
                    os.write("# export time: " + HUMAN_TIME.format(new Date()) + "\n");
                    os.write("# matched articles: " + countHits(query) + "\n");
                }

                CSVFormat csvFormat = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC);
                if (structure.contains(STRUCTURE.header)) {
                    csvFormat = csvFormat.withHeader(fields.toArray(new String[0]));
                }
                try (CSVPrinter printer = new CSVPrinter(os, csvFormat)) {
                    if (structure.contains(STRUCTURE.content)) {
                        searchAndRetrieve(request, fields, printer);
                    }
                } catch (IOException e) {
                    log.error("IOException writing Solr response for " + request);
                } catch (SolrServerException e) {
                    log.error("SolrException during search for " + request);
                }
            }
        };
    }

    private static void searchAndRetrieve(SolrParams baseRequest, Collection<String> fields, CSVPrinter csvPrinter)
            throws IOException, SolrServerException {
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        ModifiableSolrParams request = new ModifiableSolrParams(baseRequest);
        while (true) {
            request.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solrClient.query(request);
            writeResponse(response, fields, csvPrinter);
            if (cursorMark.equals(response.getNextCursorMark())) {
                return;
            }
            cursorMark = response.getNextCursorMark();
        }
    }

    private static void writeResponse(QueryResponse response, Collection<String> fields, CSVPrinter csvPrinter)
            throws IOException {
        for (SolrDocument doc: response.getResults()) {
            csvPrinter.printRecord(fields.stream().map(doc::get).collect(Collectors.toList()));
        }
    }

    private static String sanitize(String query) {
        // Quick disabling of obvious tricks
        return query.
                replaceAll("^[{]", "\\{"). // Behaviour adjustment
                replaceAll("^/", "\\/").   // Regexp matching
                replaceAll(":/", ":\\/");  // Regexp matching
    }

}
