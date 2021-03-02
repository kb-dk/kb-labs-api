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
import dk.kb.labsapi.model.TimelineDto;
import dk.kb.labsapi.model.TimelineEntryDto;
import dk.kb.labsapi.model.TimelineRequestDto;
import dk.kb.util.yaml.YAML;
import dk.kb.webservice.exception.InternalServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.json.BucketJsonFacet;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    public static SolrTimeline getInstance() {
        return instance;
    }

    public SolrTimeline() {
        super(".labsapi.aviser");
        YAML conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser.timeline");
        final int nowYear = LocalDate.now(DA).getYear();
        minYear = conf.getInteger(".minYear", 1666);
        maxYear = "NOW".equals(conf.getString(".maxYear", null)) ?
                nowYear :
                conf.getInteger(".maxYear", nowYear);
    }
    public SolrTimeline(YAML generalConf) {
        super(generalConf);
        YAML conf = generalConf.getSubMap(".labsapi.aviser.timeline");
        final int nowYear = LocalDate.now(DA).getYear();
        minYear = conf.getInteger(".minYear", 1666);
        maxYear = "NOW".equals(conf.getString(".maxYear", null)) ?
                nowYear :
                conf.getInteger(".maxYear", nowYear);
    }

    public StreamingOutput timeline(
            String query, GRANULARITY granularity, String startTime, String endTime, Collection<ELEMENT> elements,
            Set<STRUCTURE> structure, TIMELINE_FORMAT format) {
        String trueQuery = sanitize(query);
        String trueStartTime = parseTime(startTime, minYear);
        String trueEndTime = parseTime(endTime, maxYear);
        JsonQueryRequest jQuery = getTimelineRequest(granularity, elements, trueQuery, trueStartTime, trueEndTime);

        QueryResponse response;
        try {
            response = callSolr(jQuery);
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
                                  .map(TimelineRequestDto.ElementsEnum::valueOf).collect(Collectors.toList()))
                .granularity(TimelineRequestDto.GranularityEnum.valueOf(granularity.toString().toUpperCase(Locale.ROOT)));
        TimelineDto timeline = makeTimeline(request, elements, response);
        System.out.println(timeline);
        return null ; //streamTimelineResponse(timeline, structure, format);
    }

    private TimelineDto makeTimeline(TimelineRequestDto request, Collection<ELEMENT> elements, QueryResponse response) {
        TimelineDto timeline = new TimelineDto();
        timeline.setRequest(request);

        TimelineEntryDto total = new TimelineEntryDto()
                .timestamp("total")
                .articles(0L)
                .characters(0L)
                .editions(0L)
                .pages(0L)
                .paragraphs(0L)
                .uniqueTitles(0L)
                .words(0L);

        List<TimelineEntryDto> entries =
                response.getJsonFacetingResponse().getBucketBasedFacets("timeline").getBuckets().stream()
                        .map(e -> solrEntryToDto(e, elements))
                        .peek(e -> updateTotal(total, e))
                        //.sorted(Comparator.comparing(e -> e.getTimestamp())) // Order is already handled by Solr
                        .collect(Collectors.toList());
        // TODO: Handle unique publishers

        timeline.setTotal(total);
        timeline.setEntries(entries);
        return timeline;
    }

    private static TimelineEntryDto solrEntryToDto(BucketJsonFacet bucket, Collection<ELEMENT> elements) {
        // TODO: Use granularity to adjust to either YYYY or YYYY-MM
        String timestamp = Integer.toString(
                Instant.ofEpochMilli(((Date)bucket.getVal()).getTime()).atZone(Z).getYear());
        TimelineEntryDto entry =  new TimelineEntryDto().timestamp(timestamp);
        elements.forEach(e -> {
            Object num = e == ELEMENT.articles ? bucket.getCount() : bucket.getStatValue(e.toString());
            long articles = num instanceof Long ? (Long) num : ((Double) num).longValue();
            switch (e) {
                case characters: {
                    entry.setCharacters(((Double) bucket.getStatValue(e.toString())).longValue());
                    break;
                }
                case words: {
                    entry.setWords(((Double) bucket.getStatValue(e.toString())).longValue());
                    break;
                }
                case paragraphs: {
                    entry.setParagraphs(((Double) bucket.getStatValue(e.toString())).longValue());
                    break;
                }
                case articles: {
                    entry.setArticles(articles);
                    break;
                }
                case pages: {
                    entry.setPages((Long) bucket.getStatValue(e.toString()));
                    break;
                }
                case editions: {
                    entry.setEditions((Long) bucket.getStatValue(e.toString()));
                    break;
                }
                case unique_titles: {
                    entry.setUniqueTitles((Long) bucket.getStatValue(e.toString()));
                    break;
                }
            }
        });
        return entry;
    }

    private static void updateTotal(TimelineEntryDto total, TimelineEntryDto entry) {
        total.setCharacters(total.getCharacters()+entry.getCharacters());
        total.setWords(total.getWords()+entry.getWords());
        total.setParagraphs(total.getParagraphs()+entry.getParagraphs());
        total.setArticles(total.getArticles()+entry.getArticles());
        total.setPages(total.getPages()+entry.getPages());
        total.setArticles(total.getArticles()+entry.getArticles());
    }

    private JsonQueryRequest getTimelineRequest(
            GRANULARITY granularity, Collection<ELEMENT> elements,
            String trueQuery, String trueStartTime, String trueEndTime) {
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
                .withFilter("recordBase:doms_aviser") // Articles only
                // Filter is added automatically by the SolrClient
                .withParam(FacetParams.FACET, "true") // Need this for JSON faceting
                .withParam(FacetParams.FACET_FIELD, "py") // Fairly cheap field. Needed to override the default of many fields
                .withParam(FacetParams.FACET_LIMIT, "0") // As close as we come to disabling standard faceting
                .withParam(GroupParams.GROUP, "false")
                .withParam(HighlightParams.HIGHLIGHT, "false")
                .withParam(CommonParams.ROWS, Integer.toString(0));

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
        if ("*:*".equals(trueQuery)) {
            if (elements.contains(ELEMENT.pages)) {
                elementCalls.put("pages", Map.of("query", "segment_index:1"));
            }
            if (elements.contains(ELEMENT.editions)) {
                elementCalls.put("editions", Map.of("query", "segment_index:1 AND newspaper_page:1"));
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

    /**
     * @param time YYYY or YYYY-MM or blank.
     * @return a Solr timestamp.
     */
    private String parseTime(String time, int defaultYear) {
        if (time == null || time.isBlank()) {
            // TODO: 12-31T23... when end
            return defaultYear + "-01-01T00:00:00Z";
        }

        Matcher matcher;
        if ((matcher = YYYY.matcher(time)).matches()) {
            int year = Integer.parseInt(matcher.group());
            return Math.max(minYear, Math.min(maxYear, year)) + "-01-01T00:00:00Z";
        }
        if ((matcher = YYYY_MM.matcher(time)).matches()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2).replaceAll("^0", ""));
            if (month == 0) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was 0. Month indexes should start with 1");
            }
            if (month > 12) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was " + month + " which is not valid under the GregorianCalendar");
            }
            year = Math.max(minYear, Math.min(maxYear, year));
            return String.format(Locale.ROOT, "%4d-%2d-01-01T00:00:00Z", year, month);
        }
        if ("now".equals(time.toLowerCase(Locale.ROOT))) {
            LocalDate now = LocalDate.now(DA);
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-01T00:00:00Z", Locale.ROOT));
        }
        throw new IllegalArgumentException("Unsupported datetime format '" + time + "'");
    }
    private static final Pattern YYYY = Pattern.compile("[0-9]{4,4}");
    private static final Pattern YYYY_MM = Pattern.compile("([0-9]{4,4})-([01][0-9])");
    
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
