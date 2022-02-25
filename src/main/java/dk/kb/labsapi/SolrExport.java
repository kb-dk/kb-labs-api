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

import dk.kb.JSONStreamWriter;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.util.yaml.YAML;
import dk.kb.webservice.exception.InternalServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Solr data export handling.
 */
public class SolrExport extends SolrBase {
    private static final Logger log = LoggerFactory.getLogger(SolrExport.class);

    final static SimpleDateFormat HUMAN_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
    final static SimpleDateFormat EXPORT_ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    final static String LINK = "link"; // Pseudo field with link to Mediestream webpage
    final static String LINK_PREFIX_DEFAULT = "http://www2.statsbiblioteket.dk/mediestream/avis/record/";
    final static String TIMESTAMP = "timestamp";

    final static ZoneId DA = ZoneId.of("Europe/Copenhagen");
    final static ZoneId Z = ZoneId.of("Z");

    private static final SolrExport instance = new SolrExport();

    private final int pageSize;
    private final String linkPrefix;
    private final String exportSort;

    private final int minYear;
    private final int maxYear;

    public SolrExport() {
        super(".labsapi.aviser");
        YAML conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser.export");
        pageSize = conf.getInteger(".solr.pagesize", 500);
        linkPrefix = conf.getString(".link.prefix", LINK_PREFIX_DEFAULT);
        exportSort = conf.getString(".solr.sort");

        final int nowYear = LocalDate.now(DA).getYear();
        minYear = conf.getInteger(".minYear", 1666);
        maxYear = "NOW".equals(conf.getString(".maxYear", null)) ?
                nowYear :
                conf.getInteger(".maxYear", nowYear);
    }

    public static SolrExport getInstance() {
        return instance;
    }

    public enum STRUCTURE { comments, header, content ;
        public static Set<STRUCTURE> DEFAULT = new HashSet<>(Arrays.asList(header, content)) ;
        public static Set<STRUCTURE> ALL = new HashSet<>(Arrays.asList(comments, header, content)) ;
        public static Set<STRUCTURE> valueOf(List<String> vals) {
            return vals == null || vals.isEmpty() ?
                    DEFAULT :
                    vals.stream().map(STRUCTURE::valueOf).collect(Collectors.toSet());
        }
    }
    public enum EXPORT_FORMAT { csv, json, jsonl;
      public static EXPORT_FORMAT getDefault() {
          return csv;
      }
      public static EXPORT_FORMAT lenientParse(String format) {
          EXPORT_FORMAT trueFormat;
          try {
              // TODO: Also consider the "Accept"-header
              trueFormat = format == null || format.isEmpty() ?
                      getDefault() :
                      valueOf(format.toLowerCase(Locale.ROOT));
          } catch (IllegalArgumentException e) {
              throw new InvalidArgumentServiceException(
                      "Error: The format '" + format + "' is unsupported. " +
                      "Supported formats are " + Arrays.toString(values()));
          }
          return trueFormat;
      }
    }

    /**
     * Export the fields from the documents from a search for query using {@link #solrClient} by streaming.
     * @param query     restraints for the export.
     * @param fields    the fields to export.
     * @param max       the maximum number of documents to export.
     * @param structure the overall elements of the export.
     * @param format    the export format.
     * @return a lazy-evaluated stream delivering the content.
     */
    public StreamingOutput export(String query, Set<String> fields, long max, Set<STRUCTURE> structure,
                                         EXPORT_FORMAT format) {
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
                FacetParams.FACET, "false",
                CommonParams.SORT, exportSort,
                 CommonParams.FL, String.join(",", expandRequestFields(fields)));

        return format == EXPORT_FORMAT.csv ?
                streamExportCSV(request, query, fields, max, structure) :
                streamExportJSON(request, query, fields, max, structure, format);
    }

    private StreamingOutput streamExportCSV(
            SolrParams request, String query, Set<String> fields, long max, Set<STRUCTURE> structure) {
        return output -> {
            try (OutputStreamWriter os = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                if (structure.contains(STRUCTURE.comments)) {
                    os.write("# kb-labs-api export of Mediestream aviser data"+ "\n");
                    os.write("# query: " + query.replace("\n", "\\n") + "\n");
                    os.write("# fields: " + fields.toString() + "\n");
                    synchronized (HUMAN_TIME) {
                        os.write("# export time: " + HUMAN_TIME.format(new Date()) + "\n");
                    }
                    os.write("# matched articles: " + countHits(query) + "\n");
                    os.write("# max articles returned: " + max + "\n");
                }

                CSVFormat csvFormat = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC);
                if (structure.contains(STRUCTURE.header)) {
                    csvFormat = csvFormat.withHeader(fields.toArray(new String[0]));
                }
                try (CSVPrinter printer = new CSVPrinter(os, csvFormat)) {
                    Consumer<SolrDocument> docWriter = doc -> {
                        try {
                            printer.printRecord(fields.stream()
                                    .map(doc::get)
                                    .map(SolrExport::flattenStringList)
                                    .map(SolrExport::escapeCSVString)
                                    .collect(Collectors.toList()));
                        } catch (IOException e) {
                            throw new RuntimeException("Exception writing to CSVPrinter for " + request, e);
                        }
                    };

                    if (structure.contains(STRUCTURE.content)) {
                        long processed = searchAndProcess(
                                request, pageSize, max, docWriter, doc -> this.expandExportResponse(doc, fields));
                        log.debug("Wrote " + processed + " CSV entries for " + request);
                    }
                } catch (IOException e) {
                    log.error("IOException writing Solr response for " + request);
                } catch (SolrServerException e) {
                    log.error("SolrException during search for " + request);
                }
            }
        };
    }

    private StreamingOutput streamExportJSON(
            SolrParams request, String query, Set<String> fields, long max, Set<STRUCTURE> structure,
            EXPORT_FORMAT format) {
        return output -> {
            try (OutputStreamWriter osw = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                 JSONStreamWriter jw = new JSONStreamWriter(osw, JSONStreamWriter.FORMAT.valueOf(format.toString()))) {
                Consumer<SolrDocument> docWriter = doc -> jw.writeJSON(
                        fields.stream()
                                .filter(doc::containsKey)
                                // TODO: Convert to DocumentDTO
                                .collect(Collectors.toMap(
                                        field -> field, field -> flattenStringList(doc.get(field)))));

                if (structure.contains(STRUCTURE.content)) {
                    searchAndProcess(request, pageSize, max, docWriter, null);
                }
            } catch (SolrServerException e) {
                throw new RuntimeException("SolrException writing " + format + " for " + request, e);
            }
        };
    }

    private Set<String> expandRequestFields(Set<String> fields) {
        if (fields.contains(LINK) && !fields.contains("pageUUID")) { // link = URL to the page
            Set<String> expanded = new LinkedHashSet<>(fields);
            expanded.add("pageUUID");
            return expanded;
        }
        return fields;
    }
    private SolrDocument expandExportResponse(SolrDocument doc, Set<String> fields) {
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

    /* *************************************************************************************************************** */
    
    public enum FACET_FORMAT { csv;
      public static FACET_FORMAT getDefault() {
          return csv;
      }
    }
    public enum FACET_SORT { count, index;
      public static FACET_SORT getDefault() {
          return count;
      }
    }

    public StreamingOutput facet(
            String query, String startTime, String endTime, String field, FACET_SORT sort, Integer limit,
            FACET_FORMAT outFormat) {

        // TODO: Why are start and end not used?
        String trueStartTime = ParamUtil.parseTimeYearMonth(startTime, minYear, minYear, maxYear, true);
        String trueEndTime = ParamUtil.parseTimeYearMonth(endTime, maxYear, minYear, maxYear, false);

        SolrParams request = new SolrQuery(
                CommonParams.Q, sanitize(query),
                FacetParams.FACET, "true",
                FacetParams.FACET_FIELD, field,
                FacetParams.FACET_LIMIT, limit.toString(),
                FacetParams.FACET_SORT, sort.toString(),
                GroupParams.GROUP, "false",
                HighlightParams.HIGHLIGHT, "false",
                // Filter is added automatically by the SolrClient
                CommonParams.ROWS, Integer.toString(0));
        QueryResponse response;
        try {
            response = solrClient.query(request);
        } catch (Exception e) {
            log.warn("Exception calling Solr for countHits(" + query + ")", e);
            throw new InternalServiceException(
                    "Internal error counting hits for query '" + query + "': " + e.getMessage());
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC).withHeader(field, "count");

        return output -> {
            try (OutputStreamWriter os = new OutputStreamWriter(output, StandardCharsets.UTF_8) ;
                 CSVPrinter printer = new CSVPrinter(os, csvFormat)) {
                FacetField facetField = response.getFacetField(field);
                if (facetField != null) {
                    for (FacetField.Count count: facetField.getValues()) {
                        printer.printRecord(count.getName(), count.getCount());
                    }
                }
            } catch (IOException e) {
                log.error("IOException writing Solr response for facet request" + request);
            }
        };
    }

    // Is Object is a List<String> then it is flattened to a single String with newlines as delimiter
    @SuppressWarnings("unchecked")
    private static Object flattenStringList(Object value) {
        if (value instanceof List &&
            (!((List<Object>)value).isEmpty() && ((List<Object>)value).get(0) instanceof String)) {
            return String.join("\n", ((List<String>)value));
        }
        return value;
    }

}
