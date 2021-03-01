package dk.kb.labsapi.api.impl;

import dk.kb.labsapi.SolrBridge;
import dk.kb.labsapi.api.LabsapiApi;
import dk.kb.labsapi.config.ServiceConfig;
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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the OpenAPI-generated {@link LabsapiApi}.
 */
public class LabsapiService implements LabsapiApi {
    private static final Logger log = LoggerFactory.getLogger(LabsapiService.class);

    @Context
    private transient HttpServletResponse httpServletResponse;

    final static SimpleDateFormat FILENAME_ISO = new SimpleDateFormat("yyyy-MM-dd'T'HHmm", Locale.ENGLISH);
    final static Set<String> allowedAviserExportFields = new HashSet<>();
    final static Set<String> allowedFacetFields = new HashSet<>();
    private static final Integer facetLimitMax;
    static {
        String key = ".labsapi.aviser.export.fields";
        try {
            allowedAviserExportFields.addAll(ServiceConfig.getConfig().getList(key));
        } catch (Exception e) {
            log.error("Unable to retrieve list of export fields from {} in config. Export is not possible", key);
        }

        key = ".labsapi.aviser.facet.fields";
        try {
            allowedFacetFields.addAll(ServiceConfig.getConfig().getList(key));
        } catch (Exception e) {
            log.error("Unable to retrieve list of facet fields from {} in config. Facet is not possible", key);
        }
        facetLimitMax = ServiceConfig.getConfig().getInteger(".labsapi.aviser.facet.limit.max", 1000);
    }

    /**
     * Extract statistics for the newspaper corpus at http://mediestream.dk/
     *
     * @param query: Optional query for the timeline statistics. If no query is given, all data are selected. The output will be a number of timeslices with the given granularity, followed by a summary.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result.
     *
     * @param granularity: The granularity of the timeline. The finer the granularity, the longer the processing time.
     *
     * @param startTime: The starting point of the timeline (inclusive), expressed as YYYY or YYYY-MM. This cannot be earlier than 1666.
     *
     * @param endTime: The ending point of the timeline (inclusive), expressed as YYYY or YYYY-MM. If blank, the current point in time is used.  Note: As of 2021, Mediestream does nok contain newspapers from the last 8 years.
     *
     * @param elements: The elements for the timeline. The element &#39;unique_publishers&#39; is special as it, as the name signals, the number of unique puslishers and not the sum of instances.
     *
     * @param format: The delivery format.  * CSV: Comma separated, missing values represented with nothing, strings encapsulated in quotes * JSON: Valid JSON in the form of a single array of Documents * JSONL: Newline separated single-line JSON representations of Documents
     *
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Faceting aggregates statistics for a given field based on a query. E.g. faceting on &#x60;familyID&#x60; delivers a list of all unique general newspaper titles for all the articles matching the query.  The data are from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)).  Note: Depending on query and granularity, the timeline stats can take up to a few minutes to extract. Patience is adviced.
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public StreamingOutput aviserStatsTimeline(String query, String granularity, String startTime, String endTime, List<String> elements, String format) throws ServiceException {
        throw new UnsupportedOperationException("Not implemented yet");
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
        Set<String> eFields = fields.stream().
                filter(Objects::nonNull).
                filter(field -> !field.isEmpty()).
                map(field -> Arrays.asList(field.split("\\s*,\\s*"))).
                flatMap(Collection::stream).
                map(String::trim).
                collect(Collectors.toCollection(LinkedHashSet::new));

        if (!allowedAviserExportFields.containsAll(eFields)) {
            eFields.removeAll(allowedAviserExportFields);
            throw new InvalidArgumentServiceException(
                    "Error: Unsupported export fields '" + eFields + "': . " +
                    "Valid fields are " + allowedAviserExportFields);
        }
        long trueMax = max == null ? 10 : (max < 0 ? -1 : max);
        Set<SolrBridge.STRUCTURE> structureSet = SolrBridge.STRUCTURE.valueOf(structure);
        SolrBridge.EXPORT_FORMAT trueFormat;
        try {
            // TODO: Also consider the "Accept"-header
            trueFormat = format == null || format.isEmpty() ?
                    SolrBridge.EXPORT_FORMAT.getDefault() :
                    SolrBridge.EXPORT_FORMAT.valueOf(format.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentServiceException(
                    "Error: The format '" + format + "' is unsupported. " +
                    "Supported formats are " + Arrays.toString(SolrBridge.EXPORT_FORMAT.values()));
        }
        if (trueFormat != SolrBridge.EXPORT_FORMAT.csv && structureSet.contains(SolrBridge.STRUCTURE.comments)) {
            log.warn("Requested export in format {} with structure {}, " +
                     "which is not possible: Comments will not be delivered",
                     trueFormat, SolrBridge.STRUCTURE.comments);
        }
        switch (trueFormat) {
            case csv: {
                httpServletResponse.setContentType("text/csv");
                break;
            }
            case json: {
                httpServletResponse.setContentType("application/json");
                break;
            }
            case jsonl: {
                httpServletResponse.setContentType( "application/x-ndjson");
                break;
            }
            default: throw new InternalServiceException(
                    "Internal exception: format '" + trueFormat + "' could not be converted to MIME type");
        }

        log.debug(String.format(Locale.ENGLISH,
                                "Exporting fields %s with max=%d and structure=%s in format=%s for query '%s'",
                                eFields, max, structureSet.toString(), format, query));
        try{
            httpServletResponse.setHeader("Content-Disposition",
                                          "inline; filename=\"mediestream_" + getCurrentTimeISO() + "." + trueFormat + "\"");
            return SolrBridge.export(query, eFields, trueMax, structureSet, trueFormat);
        } catch (Exception e){
            throw handleException(e);
        }
    }
    private synchronized static String getCurrentTimeISO() {
        return FILENAME_ISO.format(new Date());
    }

    /**
     * Facet on one or more fields for newspapers data from http://mediestream.dk/
     *
     * @param query: A query for the newspapers to export aggregates facet statistics for.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis  A filter restricting the result to newspapers older than 140 years will be automatically applied
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
    public StreamingOutput facet(String query, String field, String sort, Integer limit, String format) throws ServiceException {
        SolrBridge.FACET_SORT eSort = sort == null || sort.isEmpty() ?
                SolrBridge.FACET_SORT.getDefault() :
                SolrBridge.FACET_SORT.valueOf(sort.toLowerCase(Locale.ROOT));
        if (eSort == null) {
            throw new InvalidArgumentServiceException("Unknown sort '" + sort + "'");
        }
        SolrBridge.FACET_FORMAT eFormat = format == null || format.isEmpty() ?
                SolrBridge.FACET_FORMAT.getDefault() :
                SolrBridge.FACET_FORMAT.valueOf(format.toLowerCase(Locale.ROOT));
        if (eFormat == null) {
            throw new InvalidArgumentServiceException("Unknown delivery format '" + format + "'");
        }
        if (!allowedFacetFields.contains(field)) {
            throw new InvalidArgumentServiceException(
                    "Cannot facet on field '" + field + "', only " + allowedFacetFields + " are acceptable");
        }

        try {
            return SolrBridge.facet(query, field, eSort, limit, eFormat);
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
    public javax.ws.rs.core.StreamingOutput hitCount(String query) throws ServiceException {
        return output -> output.write(Long.toString(SolrBridge.countHits(query)).getBytes(StandardCharsets.UTF_8));
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
