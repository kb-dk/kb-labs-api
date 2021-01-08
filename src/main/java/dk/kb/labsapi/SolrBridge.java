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
import dk.kb.labsapi.model.DocumentDto;
import dk.kb.webservice.exception.InternalServiceException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handles Solr comminication.
 */
public class SolrBridge {
    private static final Logger log = LoggerFactory.getLogger(SolrBridge.class);
    
    final static SimpleDateFormat HUMAN_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
    final static SimpleDateFormat EXPORT_ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    final static String LINK = "link"; // Pseudo field with link to Mediestream webpage
    final static String LINK_PREFIX_DEFAULT = "http://www2.statsbiblioteket.dk/mediestream/avis/record/";
    final static String TIMESTAMP = "timestamp";
    
    private static SolrClient solrClient;
    private static int pageSize = 500;
    private static String linkPrefix;
    private static String exportSort = null;
    
    public enum STRUCTURE {
        comments, header, content;
        public static Set<STRUCTURE> DEFAULT = new HashSet<>(Arrays.asList(header, content));
        public static Set<STRUCTURE> ALL = new HashSet<>(Arrays.asList(comments, header, content));
        
        public static Set<STRUCTURE> valueOf(List<String> vals) {
            return vals == null || vals.isEmpty() ?
                           DEFAULT :
                           vals.stream().map(STRUCTURE::valueOf).collect(Collectors.toSet());
        }
    }
    
    public enum FORMAT {
        csv, json, jsonl;
        
        public static FORMAT getDefault() {
            return csv;
        }
        
        public static FORMAT parse(String formatString) {
            if (formatString == null || formatString.isEmpty()) {
                return null;
            }
            String cleaned = formatString.toLowerCase(Locale.ROOT).trim().replaceAll("^[^/]*/", "");
            return valueOf(cleaned);
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
            pageSize = ServiceConfig.getConfig().getInteger(".labsapi.aviser.export.solr.pagesize", pageSize);
            linkPrefix = ServiceConfig.getConfig().getString(".labsapi.aviser.export.link.prefix", LINK_PREFIX_DEFAULT);
            exportSort = ServiceConfig.getConfig().getString(".labsapi.aviser.export.solr.sort");
            log.info("Creating SolrClient({}) with filter='{}'", fullURL, filter);
            solrClient = new HttpSolrClient.Builder(fullURL).withInvariantParams(baseParams).build();
        }
        return solrClient;
    }
    
    static {
        getClient(); // Fail early
    }
    
    /**
     * Performs the fastest possible request ({@code rows=0&facet=false...} to Solr and return the number of hits.
     * Typically used to get an idea of the size of a full export.
     *
     * @param query a Solr query.
     * @return the number of hits for the query.
     */
    public static long countHits(String query) {
        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                FacetParams.FACET, "false",
                GroupParams.GROUP, "false",
                HighlightParams.HIGHLIGHT, "false",
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
    
    /**
     * Export the fields from the documents from a search for query using {@link #getClient()} by streaming.
     *
     * @param query     restraints for the export.
     * @param fields    the fields to export.
     * @param max       the maximum number of documents to export.
     * @param structure the overall elements of the export.
     * @param format    the export format.
     * @return a lazy-evaluated stream delivering the content.
     */
    public static org.apache.cxf.jaxrs.ext.StreamingResponse<DocumentDto> export(String query,
                                                                                 Set<String> fields,
                                                                                 long max,
                                                                                 Set<STRUCTURE> structure,
                                                                                 FORMAT format) {
        if (exportSort == null) {
            String message = "Error: Unable to export: " +
                             "No export sort (.labsapi.aviser.export.solr.sort) specified in config";
            log.error(message);
            throw new InternalServiceException(message);
        }
        
        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString((int) Math.min(max == -1 ? Integer.MAX_VALUE : max, pageSize)),
                CommonParams.SORT, exportSort,
                CommonParams.FL, String.join(",", expandRequestFields(fields)));
        
        return streamResponseJSON(request, query, fields, max, structure, format);
    }
    
    //private static org.apache.cxf.jaxrs.ext.StreamingResponse<DocumentDto> streamResponseCSV(
    //        SolrParams request, String query, Set<String> fields, long max, Set<STRUCTURE> structure) {
    //    return output -> {
    //        try (OutputStreamWriter os = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
    //            if (structure.contains(STRUCTURE.comments)) {
    //                os.write("# kb-labs-api export of Mediestream aviser data" + "\n");
    //                os.write("# query: " + query.replace("\n", "\\n") + "\n");
    //                os.write("# fields: " + fields.toString() + "\n");
    //                os.write("# export time: " + HUMAN_TIME.format(new Date()) + "\n");
    //                os.write("# matched articles: " + countHits(query) + "\n");
    //                os.write("# max articles returned: " + max + "\n");
    //            }
    //
    //            CSVFormat csvFormat = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC);
    //            if (structure.contains(STRUCTURE.header)) {
    //                csvFormat = csvFormat.withHeader(fields.toArray(new String[0]));
    //            }
    //            try (CSVPrinter printer = new CSVPrinter(os, csvFormat)) {
    //                if (structure.contains(STRUCTURE.content)) {
    //                    long processed = searchAndProcess(request, fields, pageSize, max, doc -> {
    //                        try {
    //                            printer.printRecord(fields.stream()
    //                                                      .
    //                                                              map(doc::get)
    //                                                      .
    //                                                              map(SolrBridge::flattenStringList)
    //                                                      .
    //                                                              map(SolrBridge::escapeString)
    //                                                      .
    //                                                              collect(Collectors.toList()));
    //                        } catch (IOException e) {
    //                            throw new RuntimeException("Exception writing to CSVPrinter for " + request, e);
    //                        }
    //                    });
    //                    log.debug("Wrote " + processed + " CSV entries for " + request);
    //                }
    //            } catch (IOException e) {
    //                log.error("IOException writing Solr response for " + request);
    //            } catch (SolrServerException e) {
    //                log.error("SolrException during search for " + request);
    //            }
    //        }
    //    };
    //}
    
    private static org.apache.cxf.jaxrs.ext.StreamingResponse<DocumentDto> streamResponseJSON(
            SolrParams request, String query, Set<String> fields, long max, Set<STRUCTURE> structure,
            FORMAT format) {
        //https://cxf.apache.org/docs/jax-rs-basics.html#JAX-RSBasics-CXFStreamingResponse
        return writer -> {
            {
                if (structure.contains(STRUCTURE.content)) {
                    try {
                        if (format==FORMAT.json){
                            writer.getEntityStream().write("[".getBytes(StandardCharsets.UTF_8));
                        }
                        boolean[] first = {true};
                        searchAndProcess(request, fields, pageSize, max,
                                         solrDocument -> {
                                             try {
                                                 if (format==FORMAT.json && !first[0]){
                                                     writer.getEntityStream().write(",".getBytes(StandardCharsets.UTF_8));
                                                 } else {
                                                     first[0] = false;
                                                 }
                                                 writer.getEntityStream().write("\n".getBytes(StandardCharsets.UTF_8));
                                                 writer.write(Converter.fromSolrDocument(solrDocument));
                                                
                                         } catch(IOException e){
                                                 throw new RuntimeException(
                                                         "SolrException writing " + format + " for " + request, e);
                                             }
                                         });
                        if (format==FORMAT.json){
                            writer.getEntityStream().write("\n]".getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (SolrServerException e) {
                        throw new RuntimeException("SolrException writing " + format + " for " + request, e);
                    }
                }
            }
    
        };
    }
    
    /**
     * Performs paging searches for the given baseRequest, expanding the returned {@link SolrDocument}s and feeding them
     * to the processor.
     *
     * @param baseRequest query, filters etc. {@link CursorMarkParams#CURSOR_MARK_START} will be automatically added.
     * @param fields      the fields to use with {@link #expandResponse(SolrDocument, Set)}.
     * @param pageSize    the number of SolrDocuments to fetch for each request.
     * @param max         the maximum number of SolrDocuments to process.
     * @param processor   received each retrieved and expanded Solrdocument.
     * @return the number of processed documents.
     * @throws IOException         if there was a problem calling Solr.
     * @throws SolrServerException if there was a problem calling Solr.
     */
    private static long searchAndProcess(
            SolrParams baseRequest, Set<String> fields, int pageSize, long max, Consumer<SolrDocument> processor)
            throws IOException, SolrServerException {
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        ModifiableSolrParams request = new ModifiableSolrParams(baseRequest);
        AtomicLong counter = new AtomicLong(0);
        while (max == -1 || counter.get() < max) {
            request.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            request.set(CommonParams.ROWS,
                        (int) Math.min(pageSize, max == -1 ? Integer.MAX_VALUE : max - counter.get()));
            QueryResponse response = getClient().query(request);
            response.getResults().stream().map(doc -> expandResponse(doc, fields)).forEach(processor);
            counter.addAndGet(response.getResults().size());
            if (cursorMark.equals(response.getNextCursorMark()) || (max != -1 && counter.get() >= max)) {
                return counter.get();
            }
            cursorMark = response.getNextCursorMark();
        }
        return counter.get();
    }
    
    private static Set<String> expandRequestFields(Set<String> fields) {
        if (fields.contains(LINK) && !fields.contains("pageUUID")) { // link = URL to the page
            Set<String> expanded = new LinkedHashSet<>(fields);
            expanded.add("pageUUID");
            return expanded;
        }
        return fields;
    }
    
    private static SolrDocument expandResponse(SolrDocument doc, Set<String> fields) {
        if (fields.contains(LINK)) {
            if (doc.containsKey("pageUUID")) {
                // http://www2.statsbiblioteket.dk/mediestream/avis/record/doms_aviser_page%3Auuid%3Af1ca07a5-6120-4429-ad73-5870d366b960/query/hestevogn
                doc.setField(LINK, linkPrefix + URLEncoder.encode(
                        doc.getFieldValue("pageUUID").toString(), StandardCharsets.UTF_8));
            } else {
                log.warn("expandResponse: link pseudo-field requested, but no pageUUID in response");
            }
        }
        
        if (fields.contains(TIMESTAMP) && doc.containsKey(TIMESTAMP)) {
            Object timestamp = doc.getFieldValue(TIMESTAMP);
            if (timestamp instanceof Date) {
                synchronized (EXPORT_ISO) {
                    doc.setField(TIMESTAMP, EXPORT_ISO.format(timestamp));
                }
            } else {
                log.warn("The field 'timestamp' was expected to be of type Date, but was " + timestamp.getClass());
            }
        }
        
        return doc;
    }
    
    // CSVWriter should handle newline escape but doesn't!?
    private static Object escapeString(Object o) {
        return o instanceof String ? ((String) o).replace("\\", "\\\\").replace("\n", "\\n") : o;
    }
    
    // Is Object is a List<String> then it is flattened to a single String with newlines as delimiter
    @SuppressWarnings("unchecked")
    private static Object flattenStringList(Object value) {
        if (value instanceof List &&
            (!((List<Object>) value).isEmpty() && ((List<Object>) value).get(0) instanceof String)) {
            return String.join("\n", ((List<String>) value));
        }
        return value;
    }
    
    private static String sanitize(String query) {
        // Quick disabling of obvious tricks
        return query.
                            replaceAll("^[{]", "\\{"). // Behaviour adjustment
                                                               replaceAll("^/", "\\/").   // Regexp matching
                                                                                                  replaceAll(":/",
                                                                                                             ":\\/");  // Regexp matching
    }
    
}
