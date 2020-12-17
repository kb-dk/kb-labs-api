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
import joptsimple.internal.Strings;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;

import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Handles Solr comminication.
 */
public class SolrBridge {
    private static Log log = LogFactory.getLog(SolrBridge.class);

    private static SolrClient solrClient;
    private static int pageSize = 500;
    private static String exportSort = null;

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

    public static StreamingOutput export(String query, Collection<String> fields) {
        if (exportSort == null) {
            throw new InternalServiceException(
                    "No export sort (labsapi.aviser.export.solr.sort) specified in config. Unable to export");
        }
        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString(pageSize),
                CommonParams.SORT, exportSort,
                 CommonParams.FL, Strings.join(fields, ","),
                CursorMarkParams.CURSOR_MARK_PARAM, CursorMarkParams.CURSOR_MARK_START);
        
        return streamResponse(request, fields);
    }

    public static long countHits(String query) {
        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString(0));
        try {
            // TODO: Perform search
            return 87;
        } catch (Exception e) {
            log.warn("Exception calling Solr for countHits(" + query + ")", e);
            throw new InternalServiceException(
                    "Internal error counting hits for query '" + query + "': " + e.getMessage());
        }
    }

    private static StreamingOutput streamResponse(SolrParams request, Collection<String> fields) {
        return output -> {
            String[] headers = fields.toArray(new String[0]);
            try (OutputStreamWriter os = new OutputStreamWriter(output, StandardCharsets.UTF_8) ;
                 CSVPrinter printer = new CSVPrinter(os, CSVFormat.DEFAULT.withHeader(headers))) {
                while (true) {
                    // TODO: Implement
                    printer.printRecord(fields);
                }
            } catch (IOException e) {
                log.error("IOException writing Solr response for " + request);
            }
        };
    }

    private static String sanitize(String query) {
        // Quick disabling of obvious tricks
        return query.
                replaceAll("^[{]", "\\{"). // Behaviour adjustment
                replaceAll("^/", "\\/").   // Regexp matching
                replaceAll(":/", ":\\/");  // Regexp matching
    }

}
