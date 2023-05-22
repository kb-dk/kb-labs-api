package dk.kb.labsapi.api.impl;

import dk.kb.labsapi.ImageExport;
import dk.kb.labsapi.SummariseExport;
import dk.kb.labsapi.SolrExport;
import dk.kb.labsapi.SolrTimeline;
import dk.kb.labsapi.api.LabsapiApi;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.labsapi.model.HitsDto;
import dk.kb.util.yaml.YAML;
import dk.kb.webservice.exception.InternalServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import dk.kb.webservice.exception.ServiceException;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the OpenAPI-generated {@link LabsapiApi}.
 */
public class LabsapiService implements LabsapiApi {
    private static final Logger log = LoggerFactory.getLogger(LabsapiService.class);

    @Context
    private transient HttpServletResponse httpServletResponse;

    final static SimpleDateFormat FILENAME_ISO = new SimpleDateFormat("yyyy-MM-dd'T'HHmm", Locale.ENGLISH);
    ;

    final static Set<String> allowedAviserExportFields = new HashSet<>();
    final static Set<SolrTimeline.ELEMENT> allowedTimelineElements = new HashSet<>();

    final static Set<String> allowedFacetFields = new HashSet<>();
    private static final Integer facetLimitMax;
    private static final Integer maxExport;

    static {
        final YAML conf = ServiceConfig.getConfig();
        
        String key = ".labsapi.aviser.export.fields";
        try {
            allowedAviserExportFields.addAll(conf.getList(key));
        } catch (Exception e) {
            log.error("Unable to retrieve list of export fields from {} in config. Export is not possible", key);
        }

        String eKey = ".labsapi.aviser.timeline.elements";
        if (!conf.containsKey(eKey)) {
            log.info("No '{}' key in configuration, using default elements {} for timeline", eKey, SolrTimeline.DEFAULT_TIMELINE_ELEMENTS);
            allowedTimelineElements.addAll(SolrTimeline.DEFAULT_TIMELINE_ELEMENTS);
        } else {
            allowedTimelineElements.addAll(conf.getList(eKey));
        }

        key = ".labsapi.aviser.facet.fields";
        try {
            allowedFacetFields.addAll(conf.getList(key));
        } catch (Exception e) {
            log.error("Unable to retrieve list of facet fields from {} in config. Facet is not possible", key);
        }
        facetLimitMax = ServiceConfig.getConfig().getInteger(".labsapi.aviser.facet.limit.max", 1000);

        maxExport = conf.getInteger(".labsapi.aviser.imageserver.maxExport", 1000);

    }

    /**
     * Extract statistics for the newspaper corpus at http://mediestream.dk/
     *
     * @param query: Optional query for the timeline statistics. If no query is given, all data are selected. The output will be a number of timeslices with the given granularity, followed by a summary.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result.  Note: Queries other than &#39;*:*&#39; will cause the numbers for pages and editions to be approximate.
     *
     * @param filter: Optional filter for the timeline statistics. Filter restricts the result set, just as query does, with the differences that filters are always qualified, e.g. &#x60;lplace:København&#x60; and that filter is also used when calculating the percentage.  The filter &#x60;*:*&#x60; mimicks the behaviour at [Smurf](http://labs.statsbiblioteket.dk/smurf/) while the filter &#x60;recordBase:doms_aviser&#x60; restricts to newspaper articles, as opposed to both articles (which contains fulltext) and pages (which only contains other metadata).
     *
     * @param granularity: The granularity of the timeline. The finer the granularity, the longer the processing time.
     *
     * @param startTime: The starting point of the timeline (inclusive), expressed as YYYY or YYYY-MM. This cannot be earlier than 1666.
     *
     * @param endTime: The ending point of the timeline (inclusive), expressed as YYYY or YYYY-MM. If blank, the current point in time is used.  Note: As of 2021, Mediestream does not contain newspapers later than 2013.
     *
     * @param elements: The elements for the timeline. The element &#39;unique_titles&#39; is special as it, as the name signals, the number of unique titles and not the sum of instances.
     *
     * @param structure: The major parts of the delivery.  * comments: Metadata for the timeline (query, export time...), prefixed with # in CSV * header: The export field names. Only relevant for CSV as it is implicit in JSON * content: The export content itself
     *
     * @param format: The delivery format.  * CSV: Comma separated, missing values represented with nothing, strings encapsulated in quotes * JSON: Valid JSON in the form of a single array of TimelineEntrys
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = TimelineEntryDto.class, responseContainer = "List"</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Extracts a timeline of statistical elements, optionally based on a query.  The data are from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)).  Note: Depending on query and granularity, the timeline stats can take up to a few minutes to extract. Patience is adviced.
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput aviserStatsTimeline(String query, String filter, String granularity, String startTime, String endTime, List<String> elements, List<String> structure, String format) throws ServiceException {
        if (elements.isEmpty()) {
            log.debug("No timeline elements defined, using default " + SolrTimeline.DEFAULT_TIMELINE_ELEMENTS);
            elements = SolrTimeline.DEFAULT_TIMELINE_ELEMENTS.stream().map(Enum::toString).collect(Collectors.toList());
        }
        SolrTimeline.GRANULARITY trueGranularity = SolrTimeline.GRANULARITY.lenientParse(granularity);
        Set<SolrTimeline.ELEMENT> trueElements = ensureValids(
                elements, Arrays.stream(SolrTimeline.ELEMENT.values()).map(Enum::toString).collect(Collectors.toSet()),
                "timeline").stream()
                .map(SolrTimeline.ELEMENT::valueOf)
                .collect(Collectors.toSet());
        log.info("trueElements: " + trueElements + " from " + elements);
        Set<SolrTimeline.STRUCTURE> trueStructure = SolrTimeline.STRUCTURE.valueOf(structure);
        SolrTimeline.TIMELINE_FORMAT trueFormat = SolrTimeline.TIMELINE_FORMAT.lenientParse(format);

        switch (trueFormat) {
            case csv: {
                httpServletResponse.setContentType("text/csv");
                break;
            }
            case json: {
                httpServletResponse.setContentType("application/json");
                break;
            }
            default: throw new InternalServiceException(
                    "Internal exception: format '" + trueFormat + "' could not be converted to MIME type");
        }

        log.debug(String.format(Locale.ENGLISH,
                                "Timeline elements %s with structure=%s in format=%s for query '%s' and filter '%s'",
                                trueElements, trueStructure.toString(), format, query, filter));
        try{
            httpServletResponse.setHeader("Content-Disposition",
                                          "inline; filename=\"mediestream_timeline_" + getCurrentTimeISO() + "." + trueFormat + "\"");
            return SolrTimeline.getInstance().timeline(
                    query, filter, trueGranularity, startTime, endTime, trueElements, trueStructure, trueFormat);
        } catch (Exception e){
            throw handleException(e);
        }
    }

    /**
     * Retrieve metadata fields from articles in the newspaper collection at http://mediestream.dk/ (a part of the Royal Danish Library). The export is restricted to newspapers older than 100 years and will be sorted by publication date.
     *
     * @param query: A query for the newspapers to export metadata for.\\n The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis\\n A filter restricting the result to newspapers older than 100 years will be automatically applied
     *
     * @param fields: The fields to export.\\n * link: A hyperlink to the Mediestream page for the article\\n * recordID: The unique ID of the article in the Mediestream system\\n * timestamp: The publication date for the article in ISO format YYYY-MM-DDTHH:MM:SS\\n * pwa: Predicted Word Accuracy for the OCR text on a scale from 0 to 100\\n * cer: \\n * fulltext_org: The original OCR text for the article\\n * pageUUID: The ID for the page that the article appears on\\n * editionUUID: The ID for the edition that the page with the article belongs to\\n * editionId: Human readable version of the edition\\n * titleUUID: TODO: Explain this\\n * familyId: TODO: Explain this\\n * newspaper_page: The page number of the addition that the article appears on\\n * newspaper_edition: TODO: Explain this\\n * lplace: TODO: Explain this\\n * location_name: Location names extracted from the text (low quality entity recognition)\\n * location_coordinates: Coordinates for places from location_name
     *
     * @param max: The maximum number of articles to return, -1 to return all articles. *WARNING* setting this to more than 50 when using the Swagger-UI to test will probably result in the browser locking up
     *
     * @param structure: The major parts of the delivery.\\n * comments: Metadata for the export (query, export time...), prefixed with # in CSV, not shown in JSON\\n * header: The export field names. Only relevant for CSV\\n * content: The export content itself
     *
     * @param format: The delivery format.\\n * CSV: Comma separated, missing values represented with nothing, strings encapsulated in quotes\\n * JSON: Valid JSON in the form of a single array of Documents\\n * JSONL: Newline separated single-line JSON representations of Documents
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   <li>code = 400, message = "Invalid Argument", response = String.class</li>
      *   <li>code = 406, message = "Not Acceptable", response = ErrorDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public StreamingOutput exportFields(String query, List<String> fields, Long max, List<String> structure, String format) throws ServiceException {
        if (allowedAviserExportFields.isEmpty()) {
            log.error("Error: No allowed export fields defined in properties");
            throw new InternalServiceException(
                    "Error: The list of allowed export fields is empty. The cause is probably a missing " +
                    "configuration, meaning that it requires human intervention to fix");
        }
        if (fields.isEmpty()) {
            throw new InvalidArgumentServiceException(
                    "Error: No export fields defined. Valid fields are " + allowedAviserExportFields);
        }
        Set<String> eFields = ensureValids(fields, allowedAviserExportFields, "export");

        long trueMax = max == null ? 10 : (max < 0 ? -1 : max);
        Set<SolrExport.STRUCTURE> structureSet = SolrExport.STRUCTURE.valueOf(structure);
        SolrExport.EXPORT_FORMAT trueFormat = SolrExport.EXPORT_FORMAT.lenientParse(format);
        if (trueFormat != SolrExport.EXPORT_FORMAT.csv && structureSet.contains(SolrExport.STRUCTURE.comments)) {
            log.warn("Requested export in format {} with structure {}, " +
                     "which is not possible: Comments will not be delivered",
                     trueFormat, SolrExport.STRUCTURE.comments);
        }
        switch (trueFormat) {
            case csv: {
                httpServletResponse.setContentType("text/csv;charset=UTF-8");
                break;
            }
            case json:{
                httpServletResponse.setContentType("application/json");
                break;
            }
            case jsonl: {
                httpServletResponse.setContentType("application/x-ndjson");
                break;
            }
            case txt: {
                httpServletResponse.setContentType("text/plain;charset=UTF-8");
                break;
            }
            case xml: {
                httpServletResponse.setContentType("text/xml;charset=UTF-8");
                break;
            }
            default: throw new InternalServiceException(
                    "Internal exception: format '" + trueFormat + "' could not be converted to MIME type");
        }

        log.debug(String.format(Locale.ENGLISH,
                                "Exporting fields %s with max=%d and structure=%s in format=%s for query '%s'",
                                eFields, max, structureSet.toString(), format, query));
        try{
            String filename = "mediestream_" + getCurrentTimeISO() + "." + trueFormat;
            if (trueMax == -1 || trueMax > 20) {
                // Show download link in Swagger UI, inline when opened directly in browser
                // https://github.com/swagger-api/swagger-ui/issues/3832
                httpServletResponse.setHeader(
                        "Content-Disposition", "inline; swaggerDownload=\"attachment\"; filename=\"" + filename + "\"");
                // Show download link in Swagger UI, download dialog when opened directly in browser
                // httpServletResponse.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            } else {
                // Show inline in Swagger UI, inline when opened directly in browser
                httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");
            }

            return SolrExport.getInstance().export(query, eFields, trueMax, structureSet, trueFormat);
        } catch (Exception e){
            throw handleException(e);
        }
    }

    /**
     * Deliver [ALTO XML](https://www.loc.gov/standards/alto/) for a single page from http://mediestream.dk/
     *
     * @param id: Ths ID for the ALTO to retrieve. This can be  * a [Mediestream link](https://www2.statsbiblioteket.dk/mediestream/avis/record/doms_aviser_page:uuid:a9990f12-e9f0-4b1e-becc-e0d4bf514586/query/heste) * an UUID such as &#x60;a9990f12-e9f0-4b1e-becc-e0d4bf514586&#x60; * a &#x60;recordID&#x60; for an article such as &#x60;doms_newspaperCollection:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76-segment_19&#x60;
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * [ALTO XML](https://www.loc.gov/standards/alto/) contains OCR text with bounding boxes from sections  down to single word granularity. Where possible, sections are connected through attributes to form articles, which are the atomic documents discovered through [Mediestream](https://mediestream.dk/).  This service only delivers ALTOs for newspaper material that is 140+ years old.
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public String getALTO(String id) throws ServiceException {
        String uuid = SummariseExport.extractUUID(id);

        try{
            String filename = "mediestream_page_" + uuid + ".alto.xml";
            // Show inline in Swagger UI, inline when opened directly in browser
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

            return SummariseExport.getInstance().getALTO(id);
        } catch (Exception e){
            throw handleException(e);
        }
    }

    /**
     * Deliver all illustrations that are present on newspaper pages where the input query is present in text.
     * @param query to search for in the newspaper corpus
     * @param max number of results to return illustrations from. This number restricts the query, not the number of illustrations to return.
     * @return StreamingOutput containing zip file with all illustrations from query returned as jpeg images.
     */
    @Override
    public StreamingOutput exportImages(String exportFormat, String query, Integer startTime , Integer endTime, Integer max) {
        if (max > maxExport){
            log.error("Maximum value is to high. Highest value is: " + maxExport);
            throw new dk.kb.webservice.exception.InvalidArgumentServiceException("Maximum value is to high. Highest value is: " + maxExport);
        }
        switch (exportFormat){
            case "illustrations":
                try {
                    String filename = getCurrentTimeISO() + "_illustrations.zip";
                    httpServletResponse.setHeader(
                            "Content-Disposition", "attachment; filename=\"" + filename + "\"");
                    return output -> ImageExport.getInstance().getIllustrationsFromTextQueryAsStream(query,startTime, endTime, max, output, exportFormat);
                } catch (Exception e) {
                    throw handleException(e);
                }
            case "fullPage":
                try {
                    String filename = getCurrentTimeISO() + "_fullPages.zip";
                    httpServletResponse.setHeader(
                            "Content-Disposition", "attachment; filename=\"" + filename + "\"");
                    return output -> ImageExport.getInstance().getFullpageFromQueryAsStream(query, startTime, endTime, max, output, exportFormat);
                } catch (Exception e){
                    throw handleException(e);
                }
        }
        return null;
    }

    /**
     * Splits candidates on comma and ensured that all candidates are in valids.
     * @param candidates  candidate elements.
     * @param valids      all valid elements.
     * @param designation the type of elements, used when raising an excaption.
     * @return the sanitized candidates.
     * @throws InvalidArgumentServiceException if one or more candidates are not in valids.
     */
    private Set<String> ensureValids(List<String> candidates, Set<String> valids, String designation) {
        Set<String> parsed = candidates.stream().
                filter(Objects::nonNull).
                filter(field -> !field.isEmpty()).
                map(field -> Arrays.asList(field.split("\\s*,\\s*"))).
                flatMap(Collection::stream).
                map(String::trim).
                collect(Collectors.toCollection(LinkedHashSet::new));
        if (!valids.containsAll(parsed)) {
            parsed.removeAll(allowedAviserExportFields);
            throw new InvalidArgumentServiceException(
                    "Error: Unsupported " + designation + " elements '" + parsed + "': . " +
                    "Valid fields are " + valids);
        }
        return parsed;
    }

    private synchronized static String getCurrentTimeISO() {
        return FILENAME_ISO.format(new Date());
    }

    /**
     * Facet on one or more fields for newspapers data from http://mediestream.dk/
     *
     * @param query: A query for the newspapers to export aggregates facet statistics for.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis  A filter restricting the result to newspapers older than 140 years will be automatically applied
     *
     * @param startTime: The starting point of the timeline (inclusive), expressed as YYYY, YYYY-MM or YYYY-MM-DD. This cannot be earlier than 1666.
     *
     * @param endTime: The ending point of the timeline (inclusive), expressed as YYYY, YYYY-MM or YYYY-MM-DD. If blank, the current point in time is used.  Note: As of 2021, Mediestream does not contain newspapers later than 2013.
     *
     * @param field: The field to facet. Note that it is case sensitive.  * pu: \&quot;Udgivelsessted\&quot; / publication location. Where the paper was published * familyId: The name of the newspaper : py: Publication year
     *
     * @param sort: The sort order of the facet content.
     *
     * @param limit: The maximum number of entries to return for a facet field.
     *
     * @param format: The delivery format.  * CSV: Comma separated, strings encapsulated in quotes
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Faceting aggregates statistics for a given field based on a query. E.g. faceting on &#x60;pu&#x60; delivers a list of all \&quot;publishing locations\&quot; for all the articles matching the query. The data are from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)). The data are restricted to newspapers older than 140 years and will be sorted by publication date.&#39;
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput facet(String query, String startTime, String endTime, String field, String sort, Integer limit, String format) throws ServiceException {
        SolrExport.FACET_SORT eSort = sort == null || sort.isEmpty() ?
                SolrExport.FACET_SORT.getDefault() :
                SolrExport.FACET_SORT.valueOf(sort.toLowerCase(Locale.ROOT));
        if (eSort == null) {
            throw new InvalidArgumentServiceException("Unknown sort '" + sort + "'");
        }
        SolrExport.FACET_FORMAT eFormat = format == null || format.isEmpty() ?
                SolrExport.FACET_FORMAT.getDefault() :
                SolrExport.FACET_FORMAT.valueOf(format.toLowerCase(Locale.ROOT));
        if (eFormat == null) {
            throw new InvalidArgumentServiceException("Unknown delivery format '" + format + "'");
        }
        if (!allowedFacetFields.contains(field)) {
            throw new InvalidArgumentServiceException(
                    "Cannot facet on field '" + field + "', only " + allowedFacetFields + " are acceptable");
        }

        try {
            return SolrExport.getInstance().facet(query, startTime, endTime, field, eSort, limit, eFormat);
        } catch (Exception e){
            throw handleException(e);
        }
    }


    /**
     * Perform a search with the given query, returning only the number of hits. Typically used to get an estimate for the result size for an export
     *
     * @param query: A query for the newspaper articles. The query can also be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result. A filter restricting the result to newspapers older than 100 years will be automatically applied
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = Long.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public HitsDto hitCount(String query) throws ServiceException {
        long pub = SolrExport.getInstance().countHits(query);   // >= 140 years old
        long all = SolrTimeline.getInstance().countHits(query); // No age restriction
        return new HitsDto()._public(pub).restricted(all-pub);
    }

    /**
     * Ping the server to check if the server is reachable.
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   <li>code = 406, message = "Not Acceptable", response = ErrorDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public String ping() throws ServiceException {
        return "pong";
    }



    /**
    * This method simply converts any Exception into a Service exception
    * @param e: Any kind of exception
    * @return A ServiceException
    * @see dk.kb.webservice.ServiceExceptionMapper
    */
    private ServiceException handleException(Exception e) {
        if (e instanceof ServiceException) {
            return (ServiceException) e; // Do nothing - this is a declared ServiceException from within module.
        } else {// Unforseen exception (should not happen). Wrap in internal service exception
            log.error("ServiceException(HTTP 500):", e); //You probably want to log this.
            return new InternalServiceException(e.getMessage());
        }
    }

    /**
     * This method just redirects gets to WEBAPP/api to the swagger UI /WEBAPP/api/api-docs?url=WEBAPP/api/openapi.yaml
     */
    @GET
    @Path("/")
    public Response redirect(@Context MessageContext request){
        String path = request.get("org.apache.cxf.message.Message.PATH_INFO").toString();
        if (path != null && !path.endsWith("/")){
            path = path + "/";
        }
        return Response.temporaryRedirect(URI.create(request.get("org.apache.cxf.request.url") + "/api-docs?url=" + path + "openapi.yaml")).build();
    }

}
