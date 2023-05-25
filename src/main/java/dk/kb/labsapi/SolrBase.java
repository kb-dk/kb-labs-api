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
import dk.kb.util.yaml.YAML;
import dk.kb.webservice.exception.InternalServiceException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Super class for the Solr-using parts of the API.
 */
public class SolrBase {
    private static final Logger log = LoggerFactory.getLogger(SolrBase.class);
    protected final CachingSolrClient solrClient;
    protected final SolrClient rawSolrClient;

    public SolrBase(String configRoot) {
        this(resolveConfig(configRoot));
    }
    public SolrBase(YAML conf) {
        rawSolrClient = createRawClient(conf);
        SolrClient innerSolrClient = createClient(conf);
        solrClient = new CachingSolrClient(
                innerSolrClient,
                conf.getInteger(".solr.connections", 3),
                conf.getInteger(".solr.cache.maxEntries", 50),
                conf.getInteger(".solr.cache.maxAgeMS", 1*60*1000)/1000
        );
    }

    private static YAML resolveConfig(String configRoot) {
        return ServiceConfig.getConfig().getSubMap(configRoot);
    }

    /**
     * Setup the SolrClient based on the given configuration.
     * @param conf the configuration for the Solr client.
     * @return a SolrClient ready for use.
     */
    private SolrClient createClient(YAML conf) {
        String solrURL = conf.getString(".solr.url");
        String collection = conf.getString(".solr.collection");
        String fullURL = solrURL + (solrURL.endsWith("/") ? "" : "/") + collection;
        String filter = conf.getString(".solr.filter", null);

        ModifiableSolrParams baseParams = new ModifiableSolrParams();
        baseParams.set(HighlightParams.HIGHLIGHT, false);
        baseParams.set(GroupParams.GROUP, false);
        if (filter != null) {
            baseParams.set(CommonParams.FQ, filter);
        }
        log.info("Creating SolrClient({}) with filter='{}'", fullURL, filter);
        return new HttpSolrClient.Builder(fullURL).withInvariantParams(baseParams).build();
    }

    /**
     * Create a SolrClient without any filters.
     * @param conf the configuration for the Solr client.
     * @return a SolrClient ready for use.
     */
    private SolrClient createRawClient(YAML conf) {
        String solrURL = conf.getString(".solr.url");
        String collection = conf.getString(".solr.collection");
        String fullURL = solrURL + (solrURL.endsWith("/") ? "" : "/") + collection;
        String filter = conf.getString(".solr.filter", null);

        ModifiableSolrParams baseParams = new ModifiableSolrParams();
        baseParams.set(HighlightParams.HIGHLIGHT, false);
        baseParams.set(GroupParams.GROUP, false);
        log.info("Creating SolrClient({}) without filters", fullURL);
        return new HttpSolrClient.Builder(fullURL).withInvariantParams(baseParams).build();
    }

    /**
     * Performs paging searches for the given baseRequest, expanding the returned {@link SolrDocument}s
     * and feeding them to the processor.
     * @param baseRequest query, filters etc. {@link CursorMarkParams#CURSOR_MARK_START} will be automatically added.
     * @param pageSize    the number of SolrDocuments to fetch for each request.
     * @param max         the maximum number of SolrDocuments to process.
     * @param processor   received each retrieved and expanded SolrDocument.
     * @param responseExpander optionally transforms responses from Solr requests.
     *                         If the responseExpander is null, it is ignored.
     *                         If the responseExpander returns null, the response is skipped.
     * @return the number of processed documents.
     * @throws IOException if there was a problem calling Solr.
     * @throws SolrServerException if there was a problem calling Solr.
     */
    protected long searchAndProcess(
            SolrParams baseRequest, int pageSize, long max, Consumer<SolrDocument> processor,
            Function<SolrDocument, SolrDocument> responseExpander)
            throws IOException, SolrServerException {
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        ModifiableSolrParams request = new ModifiableSolrParams(baseRequest);
        AtomicLong counter = new AtomicLong(0);
        while (max == -1 || counter.get() < max) {
            int rows = (int) Math.min(pageSize, max == -1 ? Integer.MAX_VALUE : max - counter.get());
            request.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            request.set(CommonParams.ROWS, rows);
            QueryResponse response;
            response = callSolr(request);
            response.getResults().stream()
                    .map(doc -> responseExpander == null ? doc : responseExpander.apply(doc))
                    .filter(Objects::nonNull)
                    .forEach(processor);
            counter.addAndGet(response.getResults().size());
            if (cursorMark.equals(response.getNextCursorMark()) || (max != -1 && counter.get() >= max)) {
                return counter.get();
            }
            cursorMark = response.getNextCursorMark();
        }
        return counter.get();
    }
    protected void initialSolrCall(SolrParams request, Consumer<SolrDocument> processor,
                                   Function<SolrDocument, SolrDocument> responseExpander) throws Exception {
        callSolr(request).getResults().stream()
                .map(doc -> responseExpander == null ? doc : responseExpander.apply(doc))
                .filter(Objects::nonNull)
                .forEach(processor);
    }

    protected void simplifiedSolrCall(SolrParams request, Function<SolrDocument, SolrDocument> docExpander,
                                      Consumer<SolrDocument> docConsumer) throws Exception {
        if (docExpander == null) { // Use identity function if there is no docExpander
            docExpander = (doc) -> doc;
        }

        callSolr(request).getResults().stream()
                .map(docExpander)
                .filter(Objects::nonNull)
                .forEach(docConsumer);
    }

    /**
     * Perform a Solr call for the given request, ensuring that the maximum amount of concurrent connections are obeyed.
     * All matching documents are streamed back, meaning the {@code rows} parameter is ignored.
     * <p>
     * To limit the number of documents, use {@code streamSolr(myRequest).limit(maxDocs)}.
     * <p>
     * Note: This method does not apply any filtering. It is up to the caller to ensure the rules of export are obeyed.
     * @param request the request to Solr.
     * @return a stream of {@link SolrDocument}s.
     */
    protected Stream<SolrDocument> streamSolr(SolrParams request) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterateSolr(request), 0), false);
    }

    /**
     * Perform a Solr call for the given request, ensuring that the maximum amount of concurrent connections are obeyed.
     * All matching documents are delivered trough an {@link Iterator>, meaning the {@code rows} parameter is ignored.
     * @param baseRequest the request to Solr.
     * @return an iterator of {@link SolrDocument}s.
     */
    private Iterator<SolrDocument> iterateSolr(SolrParams baseRequest) {
        ModifiableSolrParams request = new ModifiableSolrParams(baseRequest);
        request.set(CommonParams.ROWS, 1000); // TODO: Make the batch size configurable
        String sort = request.get(CommonParams.SORT);
        if (sort != null && !sort.isEmpty()) {
            request.set(CommonParams.SORT, sort + ", recordID asc"); // Tie breaker
        } else {
            request.set(CommonParams.SORT, "recordID asc"); // Tie breaker
        }

        return new Iterator<>() {
            final LinkedList<SolrDocument> docs = new LinkedList<>();
            boolean hasMoreRemoteDocs = true;
            String cursorMark = CursorMarkParams.CURSOR_MARK_START;

            @Override
            public boolean hasNext() {
                if (docs.isEmpty() && hasMoreRemoteDocs) {
                    fillDocs();
                }
                return !docs.isEmpty();
            }

            @Override
            public SolrDocument next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more documents available");
                }
                return docs.removeFirst();
            }

            private void fillDocs() {
                if (!hasMoreRemoteDocs) {
                    return;
                }

                request.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark); // Adjust request for next page

                QueryResponse response;
                try {
                    response = callSolr(request);
                } catch (Exception e) {
                    throw new RuntimeException("Exception performing streaming export for " + baseRequest, e);
                }
                docs.addAll(response.getResults());

                if (docs.isEmpty()) {
                    hasMoreRemoteDocs = false;
                    return;
                }

                if (cursorMark.equals(response.getNextCursorMark())) { // Last page
                    hasMoreRemoteDocs = false;
                }
                cursorMark = response.getNextCursorMark(); // Ready for next page
            }

        };
    }

    /**
     * Performs a Solr call for the given request, ensuring that the maximum amount of concurrent connections are obeyed.
     * @param request the request to Solr.
     * @return the response from Solr.
     * @throws RuntimeException if the Solr call could not be completed.
     */
    protected QueryResponse callSolr(SolrParams request) throws SolrServerException, IOException {
        return callSolr(request, false);
    }

    /**
     * Performs a Solr call for the given request, ensuring that the maximum amount of concurrent connections are obeyed.
     * @param request the request to Solr.
     * @param useRaw if true, the filterless Solr client will be used.
     * @return the response from Solr.
     * @throws RuntimeException if the Solr call could not be completed.
     */
    protected QueryResponse callSolr(SolrParams request, boolean useRaw) throws SolrServerException, IOException {
        return useRaw ? rawSolrClient.query(request) : solrClient.query(request);
    }

    /**
     * Performs a Solr call for the given request, ensuring that the maximum amount of concurrent connections are obeyed.
     * @param request the request to Solr.
     * @return the response from Solr.
     * @throws RuntimeException if the Solr call could not be completed.
     */
    protected QueryResponse callSolr(JsonQueryRequest request) {
        return solrClient.callSolr(request);
    }

    /**
     * Performs the fastest possible request ({@code rows=0&facet=false...} to Solr and return the number of hits.
     * Typically used to get an idea of the size of a full export.
     * @param query a Solr query.
     * @return the number of hits for the query.
     */
    public long countHits(String query) {
        return countHits(false, query);
    }

    /**
     * Performs the fastest possible request ({@code rows=0&facet=false...} to Solr and return the number of hits.
     * Typically used to get an idea of the size of a full export.
     * @param useRaw if true, the filterless Solr client will be used for counting.
     * @param query a Solr query.
     * @param filterQueries 0 or more filterQueries. These are only guaranteed to take effect if {@code useRaw == true}.
     * @return the number of hits for the query.
     */
    public long countHits(boolean useRaw, String query, String... filterQueries) {
        SolrQuery request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                FacetParams.FACET, "false",
                GroupParams.GROUP, "false",
                HighlightParams.HIGHLIGHT, "false",
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString(0));
        if (filterQueries.length != 0) {
            request.setFilterQueries(filterQueries);
        }
        try {
            QueryResponse response = callSolr(request, useRaw);
            return response.getResults().getNumFound();
        } catch (Exception e) {
            log.warn("Exception calling Solr for countHits(" + query + ")", e);
            throw new InternalServiceException(
                    "Internal error counting hits for query '" + query + "': " + e.getMessage());
        }
    }

    /**
     * Sanitize the given Solr query against the most obvious tricks (regexp bombs and behaviour modification).
     * @param query a Solr query.
     * @return a hopefully more safe Solr query.
     */
    protected static String sanitize(String query) {
        if (query == null) {
            return null;
        }
        if ("*".equals(query)) {
            query = "*:*";
        }
        return query.trim().
                replaceAll("^[{]", "\\{"). // Behaviour adjustment
                replaceAll("^/", "\\/").   // Regexp matching
                replaceAll("([^\\\\])/", "$1\\/");  // Regexp matching
    }

    /**
     * The CSVWriter should handle excaping, but does not seem to do so. If {@code o} is a String, quotes and newlines
     * are escaped.
     * @param o the object to potentially escape.
     * @return o in escaped form, ready foir CSV-write.
     */
    protected static Object escapeCSVString(Object o) {
        return o instanceof String ? ((String)o).replace("\\", "\\\\").replace("\n", "\\n") : o;
    }

}
