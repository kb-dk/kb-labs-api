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
import dk.kb.labsapi.model.TimelineDto;
import dk.kb.labsapi.model.TimelineEntryDto;
import dk.kb.labsapi.model.TimelineRequestDto;
import dk.kb.util.yaml.YAML;
import dk.kb.webservice.exception.InternalServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.request.json.QueryFacetMap;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.json.BucketBasedJsonFacet;
import org.apache.solr.client.solrj.response.json.BucketJsonFacet;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Solr timeline handling for the aviser corpus at Mediestream.
 */
public class SolrTimeline extends SolrBase {
    public final static Set<ELEMENT> DEFAULT_TIMELINE_ELEMENTS = Set.of(ELEMENT.pages, ELEMENT.editions);
    private static final Logger log = LoggerFactory.getLogger(SolrTimeline.class);

    final static SimpleDateFormat HUMAN_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
    final static String TIMESTAMP = "timestamp";
    final static ZoneId DA = ZoneId.of("Europe/Copenhagen");
    final static ZoneId Z = ZoneId.of("Z");

    private final int minYear;
    private final int maxYear;

    private static final SolrTimeline instance = new SolrTimeline();
    private final String defaultTimelineFilter;

    public static SolrTimeline getInstance() {
        return instance;
    }

    public SolrTimeline() {
        super(".labsapi.aviser.timeline");
        YAML conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser.timeline");
        final int nowYear = LocalDate.now(DA).getYear();
        minYear = conf.getInteger(".minYear", 1666); // TODO: Round depending on granularity
        maxYear = "NOW".equals(conf.getString(".maxYear", null)) ?
                nowYear :
                conf.getInteger(".maxYear", nowYear);
        defaultTimelineFilter = conf.getString(".defaultFilter", "recordBase:doms_aviser");
    }
    public SolrTimeline(YAML generalConf) {
        super(generalConf);
        YAML conf = generalConf.getSubMap(".labsapi.aviser.timeline");
        final int nowYear = LocalDate.now(DA).getYear();
        minYear = conf.getInteger(".minYear", 1666);
        maxYear = "NOW".equals(conf.getString(".maxYear", null)) ?
                nowYear :
                conf.getInteger(".maxYear", nowYear);
        defaultTimelineFilter = conf.getString(".defaultFilter", "recordBase:doms_aviser");
    }

    public StreamingOutput timeline(
            String query, String filter, GRANULARITY granularity,
            String startTime, String endTime, Collection<ELEMENT> elements,
            Set<STRUCTURE> structure, TIMELINE_FORMAT format) {
        TimelineDto timeline = getTimeline(query, filter, granularity, startTime, endTime, elements);
        return streamTimeline(timeline, structure, format);
    }

    TimelineDto getTimeline(
            String query, String filter, GRANULARITY granularity,
            String startTime, String endTime, Collection<ELEMENT> elements) {
        String trueQuery = sanitize(query);
        String trueFilter = filter == null || filter.isBlank() ? defaultTimelineFilter : sanitize(filter);
        String trueStartTime = parseTime(startTime, minYear, true);
        String trueEndTime = parseTime(endTime, maxYear, false);
        JsonQueryRequest jQuery =
                getTimelineRequest(granularity, elements, trueQuery, trueFilter, trueStartTime, trueEndTime);
        JsonQueryRequest jQueryAll =
                getTimelineRequest(granularity, elements, "*:*", trueFilter, trueStartTime, trueEndTime);

        QueryResponse response;
        QueryResponse responseAll;
        try {
            response = callSolr(jQuery);
            responseAll = callSolr(jQueryAll);
        } catch (Exception e) {
            log.warn("Exception calling Solr for timeline(" + query + ")", e);
            throw new InternalServiceException(
                    "Internal error requesting timeline for '" + query + "': " + e.getMessage());
        }

        String requestTime;
        synchronized (HUMAN_TIME) {
            requestTime = HUMAN_TIME.format(new Date());
        }
        // TODO: Handle timezone
        TimelineRequestDto request = new TimelineRequestDto()
                .startTime(trueStartTime.substring(0, 7)) // YYYY-MM
                .endTime(trueEndTime.substring(0, 7)) // YYYY-MM
                .query(query)
                .requestTime(requestTime)
                .elements(elements.stream()
                                  .map(e -> e.toString().toUpperCase(Locale.ROOT))
                                  .map(TimelineRequestDto.ElementsEnum::valueOf)
                                  .collect(Collectors.toList()))
                .granularity(TimelineRequestDto.GranularityEnum.valueOf(granularity.toString().toUpperCase(Locale.ROOT)));
        return makeTimeline(request, elements, response, responseAll);
    }

    private StreamingOutput streamTimeline(TimelineDto timeline, Set<STRUCTURE> structure, TIMELINE_FORMAT format) {
        switch (format) {
            case csv: return streamTimelineCSV(timeline, structure);
            case json: return streamTimelineJSON(timeline);
            default: throw new UnsupportedOperationException("The format '" + format + "' is unsupported");
        }
    }

    private StreamingOutput streamTimelineJSON(TimelineDto timeline) {
        return output -> {
            try (OutputStreamWriter osw = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                 JSONStreamWriter jw = new JSONStreamWriter(osw, JSONStreamWriter.FORMAT.json)) {
                jw.writeJSON(timeline);
            }
        };
    }

    // TODO: Proper ordering of structure
    private StreamingOutput streamTimelineCSV(TimelineDto timeline, Set<STRUCTURE> structure) {
        final TimelineRequestDto req = timeline.getRequest();
        final String[] headers = Stream.concat(
                Stream.concat(Stream.of("timestamp"),
                              req.getElements().stream().map(Object::toString)),
                req.getElements().stream().map(o -> o + "_percentage"))
                .toArray(String[]::new);

        return output -> {
            try (OutputStreamWriter os = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                if (structure.contains(STRUCTURE.comments)) {
                    os.write("# kb-labs-api timeline of Mediestream aviser data"+ "\n");
                    os.write("# query: " + req.getQuery().replace("\n", "\\n") + "\n");
                    os.write("# startTime: " + req.getStartTime() + "\n");
                    os.write("# endTime: " + req.getEndTime() + "\n");
                    os.write("# granularity: " + req.getGranularity() + "\n");
                    os.write("# elements: " + req.getElements() + "\n");
                    os.write("# export time: " + req.getRequestTime() + "\n");
                    os.write("# matched articles: " + timeline.getTotal().getArticles() + "\n");
                }

                CSVFormat csvFormat = CSVFormat.DEFAULT
                        .withQuoteMode(QuoteMode.NON_NUMERIC)
                        .withRecordSeparator("\n");
                if (structure.contains(STRUCTURE.header)) {
                    csvFormat = csvFormat.withHeader(headers);
                }
                try (CSVPrinter printer = new CSVPrinter(os, csvFormat)) {
                    timeline.getEntries().forEach(entry -> SolrTimeline.print(printer, entry, req.getElements()));
                    SolrTimeline.print(printer, timeline.getTotal(), req.getElements());
                }
            } catch (Exception e) {
                log.error("IOException writing Timeline response", e);
            }
        };
    }

    private static void print(
            CSVPrinter printer, TimelineEntryDto entry, List<TimelineRequestDto.ElementsEnum> elements) {
        List<Object> cells = rowToCells(entry, elements);
        try {
            printer.printRecord(cells);
        } catch (IOException e) {
            throw new RuntimeException("Exception writing CSV entry for " + entry, e);
        }
    }

    private static List<Object> rowToCells(TimelineEntryDto entry, List<TimelineRequestDto.ElementsEnum> elements) {
        List<Object> cells = new ArrayList<>(elements.size() + 1);
        cells.add(entry.getTimestamp()); // Mandatory
        addCells(entry, elements, cells, false);
        addCells(entry, elements, cells, true);
        return cells;
    }

    private static void addCells(TimelineEntryDto entry, List<TimelineRequestDto.ElementsEnum> elements,
                                 List<Object> cells, boolean percentage) {
        elements.forEach(element -> {
            switch (element) {
                case CHARACTERS: {
                    if (percentage) {
                        cells.add(entry.getCharactersPercentage());
                    } else {
                        cells.add(entry.getCharacters());
                    }
                    break;
                }
                case WORDS: {
                    if (percentage) {
                        cells.add(entry.getWordsPercentage());
                    } else {
                        cells.add(entry.getWords());
                    }
                    break;
                }
                case PARAGRAPHS: {
                    if (percentage) {
                        cells.add(entry.getParagraphsPercentage());
                    } else {
                        cells.add(entry.getParagraphs());
                    }
                    break;
                }
                case PAGES: {
                    if (percentage) {
                        cells.add(entry.getPagesPercentage());
                    } else {
                        cells.add(entry.getPages());
                    }
                    break;
                }
                case ARTICLES: {
                    if (percentage) {
                        cells.add(entry.getArticlesPercentage());
                    } else {
                        cells.add(entry.getArticles());
                    }
                    break;
                }
                case EDITIONS: {
                    if (percentage) {
                        cells.add(entry.getEditionsPercentage());
                    } else {
                        cells.add(entry.getEditions());
                    }
                    break;
                }
                case UNIQUE_TITLES: {
                    if (percentage) {
                        cells.add(entry.getUniqueTitlesPercentage());
                    } else {
                        cells.add(entry.getUniqueTitles());
                    }
                    break;
                }
                default: throw new UnsupportedOperationException("Unknown element '" + element + "'");
            }
        });
    }

    private TimelineDto makeTimeline(TimelineRequestDto request, Collection<ELEMENT> elements,
                                     QueryResponse response, QueryResponse responseAll) {
        TimelineDto timeline = new TimelineDto();
        timeline.setRequest(request);

        TimelineEntryDto total = createBlankEntry("total", elements);
        TimelineEntryDto totalAll = createBlankEntry("total", elements);

        if (response.getJsonFacetingResponse() == null) { // No hits at all
            // TODO: Add support for zero-timeline
            throw new IllegalStateException(
                    "Sorry, no hits with the given constraints and zero-timeline support has not been added yet");
        }
        BucketBasedJsonFacet buckets = response.getJsonFacetingResponse().getBucketBasedFacets("timeline");
        BucketBasedJsonFacet bucketsAll = responseAll.getJsonFacetingResponse().getBucketBasedFacets("timeline");

        // TODO: Consider filling in the blanks
        List<TimelineEntryDto> entries = bucketsToEntries(request, elements, total, buckets);
        List<TimelineEntryDto> entriesAll = bucketsToEntries(request, elements, totalAll, bucketsAll);
        enrichWithPercentages(elements, entries, entriesAll);
        // TODO: Handle unique publishers
         setPercentages(elements, total, totalAll);
        
        timeline.setTotal(total);
        timeline.setEntries(entries);
        return timeline;
    }

    private void enrichWithPercentages(
            Collection<ELEMENT> elements, List<TimelineEntryDto> entries, List<TimelineEntryDto> entriesAll) {
        // Create fast lookup
        Map<String, TimelineEntryDto> cache = entriesAll.stream()
                .collect(Collectors.toMap(TimelineEntryDto::getTimestamp, e -> e));
        entries.forEach(e -> setPercentages(elements, e, cache.get(e.getTimestamp())));
    }

    private void setPercentages(Collection<ELEMENT> elements, TimelineEntryDto entry, TimelineEntryDto entryAll) {
        if (entryAll == null) {
            throw new InternalServiceException(
                    "Error: Attempting to calculate percentages for entry without a corresponding normaliser");
        }
        elements.forEach(e -> {
            switch (e) {
                case characters: {
                    entry.setCharactersPercentage(percentage(entry.getCharacters(), entryAll.getCharacters()));
                    break;
                }
                case words: {
                    entry.setWordsPercentage(percentage(entry.getWords(), entryAll.getWords()));
                    break;
                }
                case paragraphs: {
                    entry.setParagraphsPercentage(percentage(entry.getParagraphs(), entryAll.getParagraphs()));
                    break;
                }
                case articles: {
                    entry.setArticlesPercentage(percentage(entry.getArticles(), entryAll.getArticles()));
                    break;
                }
                case pages: {
                    entry.setPagesPercentage(percentage(entry.getPages(), entryAll.getPages()));
                    break;
                }
                case editions: {
                    entry.setEditionsPercentage(percentage(entry.getEditions(), entryAll.getEditions()));
                    break;
                }
                case unique_titles: {
                    entry.setUniqueTitlesPercentage(percentage(entry.getUniqueTitles(), entryAll.getUniqueTitles()));
                    break;
                }
            }
        });
    }

    private Double percentage(Long count, Long countAll) {
        return countAll == 0 ? 0.0 : count * 100.0 / countAll;
    }

    private List<TimelineEntryDto> bucketsToEntries(TimelineRequestDto request, Collection<ELEMENT> elements,
                                                    TimelineEntryDto total, BucketBasedJsonFacet buckets) {
        return buckets == null ? Collections.emptyList() :
                buckets.getBuckets()
                        .stream()
                        .map(e -> solrEntryToDto(e, elements, request.getGranularity()))
                        .peek(e -> updateTotal(total, e, elements))
                        //.sorted(Comparator.comparing(e -> e.getTimestamp())) // Order is already handled by Solr
                        .collect(Collectors.toList());
    }

    private static TimelineEntryDto createBlankEntry(String timestamp, Collection<ELEMENT> elements) {
        TimelineEntryDto entry = new TimelineEntryDto().timestamp(timestamp);
        elements.forEach(e -> {
            switch (e) {
                case characters: {
                    entry.setCharacters(0L);
                    entry.setCharactersPercentage(0.0);
                    break;
                }
                case words: {
                    entry.setWords(0L);
                    entry.setWordsPercentage(0.0);
                    break;
                }
                case paragraphs: {
                    entry.setParagraphs(0L);
                    entry.setParagraphsPercentage(0.0);
                    break;
                }
                case articles: {
                    entry.setArticles(0L);
                    entry.setArticlesPercentage(0.0);
                    break;
                }
                case pages: {
                    entry.setPages(0L);
                    entry.setPagesPercentage(0.0);
                    break;
                }
                case editions: {
                    entry.setEditions(0L);
                    entry.setEditionsPercentage(0.0);
                    break;
                }
                case unique_titles: {
                    entry.setUniqueTitles(0L);
                    entry.setUniqueTitlesPercentage(0.0);
                    break;
                }
            }
        });
        return entry;
    }

    private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private static TimelineEntryDto solrEntryToDto(
            BucketJsonFacet bucket, Collection<ELEMENT> elements, String granularity) {
        // TODO: Use granularity to adjust to either YYYY or YYYY-MM
        ZonedDateTime zonedDate = Instant.ofEpochMilli(((Date)bucket.getVal()).getTime()).atZone(Z);

        String timestamp = "YEAR".equals(granularity.toUpperCase(Locale.ROOT)) ?
                YYYY.format(zonedDate) : YYYY_MM.format(zonedDate);
        TimelineEntryDto entry =  createBlankEntry(timestamp, elements);
        elements.forEach(e -> {
            Object num = e == ELEMENT.articles ? bucket.getCount() : bucket.getStatValue(e.toString());
            long count = num == null ? 0L : // null means no match in the time slice
                    num instanceof Long ? (Long) num : ((Double) num).longValue();
            switch (e) {
                case characters: {
                    entry.setCharacters(count);
                    break;
                }
                case words: {
                    entry.setWords(count);
                    break;
                }
                case paragraphs: {
                    entry.setParagraphs(count);
                    break;
                }
                case pages: {
                    entry.setPages(count);
                    break;
                }
                case articles: {
                    entry.setArticles(count);
                    break;
                }
                case editions: {
                    entry.setEditions(count);
                    break;
                }
                case unique_titles: {
                    entry.setUniqueTitles(count);
                    break;
                }
            }
        });
        return entry;
    }

    private static void updateTotal(TimelineEntryDto total, TimelineEntryDto entry, Collection<ELEMENT> elements) {
        elements.forEach(e -> {
            switch (e) {
                case characters: {
                    total.setCharacters(total.getCharacters()+entry.getCharacters());
                    break;
                }
                case words: {
                    total.setWords(total.getWords()+entry.getWords());
                    break;
                }
                case paragraphs: {
                    total.setParagraphs(total.getParagraphs()+entry.getParagraphs());
                    break;
                }
                case pages: {
                    total.setPages(total.getPages()+entry.getPages());
                    break;
                }
                case articles: {
                    total.setArticles(total.getArticles()+entry.getArticles());
                    break;
                }
                case editions: {
                    total.setEditions(total.getEditions()+entry.getEditions());
                    break;
                }
                case unique_titles: {
                    total.setUniqueTitles(0L); // Does not make sense for the total
                    break;
                }
            }
        });
    }

    private JsonQueryRequest getTimelineRequest(
            GRANULARITY granularity, Collection<ELEMENT> elements,
            String trueQuery, String trueFilter, String trueStartTime, String trueEndTime) {
        String gap;
        switch (granularity) {
            case decade: gap = "+10YEARS";
                break;
            case year: gap = "+1YEAR";
                break;
            case month: gap = "+1MONTH";
                break;
            default: throw new UnsupportedOperationException("The granilarity '" + granularity + "' is unsupported");
        }
        // recordBase: doms_aviser (article), doms_aviser_page, doms_aviser_authority
        JsonQueryRequest jQuery = new JsonQueryRequest()
                .setQuery(trueQuery)
                // Filter is added automatically by the SolrClient
                .withParam(FacetParams.FACET, "true") // Need this for JSON faceting
                .withParam(FacetParams.FACET_FIELD, "py") // Fairly cheap field. Needed to override the default of many fields
                .withParam(FacetParams.FACET_LIMIT, "0") // As close as we come to disabling standard faceting
                .withParam(GroupParams.GROUP, "false")
                .withParam(HighlightParams.HIGHLIGHT, "false")
                .withParam(CommonParams.ROWS, Integer.toString(0));
        if (!"*:*".equals(trueFilter)) {
            // Default is "recordBase:doms_aviser" (articles only)
            jQuery = jQuery.withFilter(trueFilter);
        }

        Map<String, Object> elementCalls = new HashMap<>();
        if (elements.contains(ELEMENT.characters)) {
            elementCalls.put("characters", "sum(statChars)");
        }
        if (elements.contains(ELEMENT.words)) {
            elementCalls.put("words", "sum(statWords)");
        }
        if (elements.contains(ELEMENT.paragraphs)) {
            elementCalls.put("paragraphs", "sum(statBlocks)");
        }
        // TODO: Figure out why pages & editions does not work when using QueryFacetMap
        if ("*:*disabledfornow".equals(trueQuery)) {
            if (elements.contains(ELEMENT.pages)) {
                // https://lucene.apache.org/solr/guide/8_3/json-facet-api.html
                elementCalls.put("pages", new QueryFacetMap("segment_index:1"));
            }
            if (elements.contains(ELEMENT.editions)) {
                elementCalls.put("editions", new QueryFacetMap("segment_index:1 AND newspaper_page:1"));
//                elementCalls.put("editions", Map.of("query", "segment_index:1 AND newspaper_page:1"));
            }
        } else {
            if (elements.contains(ELEMENT.pages)) {
                elementCalls.put("pages", "unique(pageUUID)");
            }
            if (elements.contains(ELEMENT.editions)) {
                elementCalls.put("editions", "unique(editionUUID)");
            }
        }
        if (elements.contains(ELEMENT.unique_titles)) {
            elementCalls.put("unique_titles", "unique(titleUUID)"); // Approximate when > 100 entries
        }

        jQuery.withFacet("timeline", Map.of(
                "type", "range",
                "field", "timestamp",
                "start", trueStartTime,
                "end", trueEndTime,
                "gap", gap,
                "facet", elementCalls
                )
        );

        return jQuery;
    }

    /* ************************************************************************************************************** */

    private String parseTime(String time, int defaultYear, boolean first) {
        return ParamUtil.parseTimeYearMonth(time, defaultYear, minYear, maxYear, first);
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
    public enum TIMELINE_FORMAT { csv, json;
      public static TIMELINE_FORMAT getDefault() {
          return csv;
      }
      public static TIMELINE_FORMAT lenientParse(String format) {
          TIMELINE_FORMAT trueFormat;
          try {
              // TODO: Also consider the "Accept"-header
              trueFormat = format == null || format.isEmpty() ?
                      getDefault() :
                      valueOf(format.toLowerCase(Locale.ROOT));
          } catch (IllegalArgumentException e) {
              throw new InvalidArgumentServiceException(
                      "Error: The timeline export format '" + format + "' is unsupported. " +
                      "Supported formats are " + Arrays.toString(values()));
          }
          return trueFormat;
      }
    }

    public enum GRANULARITY { decade, year, month;
      public static GRANULARITY getDefault() {
          return year;
      }
      public static GRANULARITY lenientParse(String format) {
          GRANULARITY trueFormat;
          try {
              // TODO: Also consider the "Accept"-header
              trueFormat = format == null || format.isEmpty() ?
                      getDefault() :
                      valueOf(format.toLowerCase(Locale.ROOT));
          } catch (IllegalArgumentException e) {
              throw new InvalidArgumentServiceException(
                      "Error: The granularity '" + format + "' is unsupported. " +
                      "Supported granularities are " + Arrays.toString(values()));
          }
          return trueFormat;
      }
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

    public enum ELEMENT {characters, words, paragraphs, articles, pages, editions, unique_titles}
}
