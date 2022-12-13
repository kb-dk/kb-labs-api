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

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
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
import org.apache.solr.common.params.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
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

    public final int pageSize;
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
    public enum EXPORT_FORMAT { csv, json, jsonl, txt, xml, image;
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

        switch (format) {
            case csv:   return streamExportCSV( request, query, fields, max, structure);
            case json:  return streamExportJSON(request, query, fields, max, structure, format);
            case jsonl: return streamExportJSON(request, query, fields, max, structure, format);
            case txt:   return streamExportTXT( request, fields, max, structure);
            case xml:   return streamExportXML( request, query, fields, max, structure);
            case image: return streamExportImages(request); // TODO: Remove image here as it runs as own endpoint in own class
            default: throw new UnsupportedOperationException("The format '" + format + "' is unsupported");
        }
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

    /**
     * Export Solr response as raw text.
     * This can be useful, when loading data into a text analysis tool as Voyant.
     */
    private StreamingOutput streamExportTXT(
            SolrParams request, Set<String> fields, long max, Set<STRUCTURE> structure) {
        return output -> {
            try (OutputStreamWriter os = new OutputStreamWriter(output, "UTF-8")) {
                // \n\n is used to create a simple distinction between results
                try (PrintWriter printer = new PrintWriter(os)) {
                    Consumer<SolrDocument> docWriter = doc -> {
                        printer.print(
                                fields.stream()
                                        .map(doc::get)
                                        .map(SolrExport::flattenStringList)
                                        .map(String::valueOf)
                                        .collect(Collectors.joining("\n")) + "\n\n"
                        );
                    };
                    if (structure.contains(STRUCTURE.content)) {
                        long processed = 0;
                        try {
                            processed = searchAndProcess(
                                    request, pageSize, max, docWriter, doc -> this.expandExportResponse(doc, fields));
                        } catch (SolrServerException e) {
                            log.error("SolrException during search for " + request);
                        }
                        log.debug("Wrote " + processed + " TXT entries for " + request);
                    }
                }
            }
        };
    }

    private StreamingOutput streamExportXML(SolrParams request, String query, Set<String> fields, long max, Set<STRUCTURE> structure){
        return output -> {
            XMLOutputFactory out = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = null;
            try {
                writer = new IndentingXMLStreamWriter(out.createXMLStreamWriter(output));
                writer.writeStartDocument();
                if (structure.contains(STRUCTURE.comments)) {
                    writer.writeComment("kb-labs-api export of Mediestream aviser data");
                    writer.writeComment("query: " + query.replace("\n", "\\n"));
                    writer.writeComment("fields: " + fields.toString());
                    synchronized (HUMAN_TIME) {
                        writer.writeComment("export time: " + HUMAN_TIME.format(new Date()));
                    }
                    writer.writeComment("matched articles: " + countHits(query));
                    writer.writeComment("max articles returned: " + max);
                }
                writer.writeStartElement("results");
                // root <results>
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
            XMLStreamWriter finalWriter = writer;
            Consumer<SolrDocument> docWriter = doc -> {

                try {
                    finalWriter.writeStartElement("result");
                    fields.stream()
                            .filter(doc::containsKey)
                            .forEach(field -> {
                            try {
                                finalWriter.writeStartElement(field);
                                finalWriter.writeCharacters(doc.getFieldValue(field).toString());
                                finalWriter.writeEndElement();
                            } catch (XMLStreamException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    finalWriter.writeEndElement();
                } catch (XMLStreamException e) {
                    throw new RuntimeException(e);
                }

            };
            if (structure.contains(STRUCTURE.content)) {
                long processed = 0;
                try {
                    processed = searchAndProcess(
                            request, pageSize, max, docWriter, doc -> this.expandExportResponse(doc, fields));
                } catch (SolrServerException e) {
                    log.error("SolrException during search for " + request);
                }
                log.debug("Wrote " + processed + " TXT entries for " + request);
            }
            try {
                // root </results>
                writer.writeEndElement();
                writer.writeEndDocument();
                writer.flush();
                writer.close();
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public StreamingOutput streamExportImages(SolrParams request) {
        // Manually constructed SolrQuery that returns illustrations metadata for each hit
        // TODO: This method has to be accesed from own OpenAPI endpoint and not only as an option in export
        String format = "json"; // Defines the format for Solr response - needs to be json for ImageExtractor class to function correctly
        // Sets fields to export
        Set<String> fields = new HashSet<>();
        fields.add("recordID"); // Is not used for anything
        fields.add("pageUUID"); // Defines the page
        fields.add("illustration"); // Used to extract every illustration for each pageUUID
        fields.add("page_width"); // Overall width of page - used to calculate extraction region
        fields.add("page_height"); //Overall height of page - used to calculate extraction region
        // Maximum number of documents to export
        long max = 10; // should be set by user
        // Makes the request modifialble
        ModifiableSolrParams finalRequest = new ModifiableSolrParams(request);
        // Add fields to SolrParams
        // Constructs solr query that only contains pages with illustrations
        SolrParams manualParams = new SolrQuery(
                //cykel AND py:[1850 TO 1880] AND illustration:[* TO *] this query works in the solr admin panel
                CommonParams.Q, sanitize("illustration:[* TO *]"),
                CommonParams.FL, String.join(",", expandRequestFields(fields))
        );

        // Add extra SolrParams to final request
        finalRequest.add(manualParams);

        List<STRUCTURE> structure = new ArrayList<>(){};
        structure.add(STRUCTURE.valueOf("content"));

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
                    searchAndProcess(finalRequest, pageSize, max, docWriter, null);
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
            FACET_FORMAT outFormat) throws ParseException {

        String trueStartTime = ParamUtil.parseTimeYearMonth(startTime, minYear, minYear, maxYear, true);
        String trueEndTime = ParamUtil.parseTimeYearMonth(endTime, maxYear, minYear, maxYear, false);
        //TODO: Timestamp is currently added in query. Needs to be filter query, but baseParams from SolrBase.createClient overwrites the filter query
        // Filterquery cant be in invariance, cause it then gets overwritten
        SolrQuery request = new SolrQuery(
                CommonParams.Q, sanitize("("+ query + ")" + "AND timestamp:[" + trueStartTime + " TO " + trueEndTime + "]"),
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
