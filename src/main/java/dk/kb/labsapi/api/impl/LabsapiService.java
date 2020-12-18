package dk.kb.labsapi.api.impl;

import dk.kb.labsapi.SolrBridge;
import dk.kb.labsapi.api.LabsapiApi;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.webservice.exception.InternalServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import dk.kb.webservice.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
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
 * labsapi
 *
 * <p>This pom can be inherited by projects wishing to integrate to the SBForge development platform. 
 *
 */
public class LabsapiService implements LabsapiApi {
    private static final Logger log = LoggerFactory.getLogger(LabsapiService.class);

    @Context
    private transient HttpServletResponse httpServletResponse;

    final static SimpleDateFormat SIMPLE_ISO = new SimpleDateFormat("yyyy-MM-dd'T'HHmm", Locale.ENGLISH);
    final static Set<String> allowedAviserExportFields = new HashSet<>();
    static {
        String key = ".labsapi.aviser.export.fields";
        try {
            allowedAviserExportFields.addAll(ServiceConfig.getConfig().getList(key));
        } catch (Exception e) {
            log.error("Unable to retrieve list of export fields from " + key + " in config. Export is not possible");
        }
    }

    /**
     * Retrieve metadata fields from articles in the newspaper collection at http://mediestream.dk/ (a part of the Royal Danish Library). The export is restricted to newspapers older than 100 years and will be sorted by publication date.
     *
     * @param query: A query for the newspapers to export metadata for. The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis A filter restricting the result to newspapers older than 100 years will be automatically applied
     *
     * @param fields: The fields to export. * link: A hyperlink to the Mediestream page for the article * recordID: The unique ID of the article in the Mediestream system * pwa: Predicted Word Accuracy for the OCR text on a scale from 0 to 100 * text: The text content for the article
     *
     * @param dryrun: Dry run: If true, only the count of the number of matching articles is returned
     *
     * @param structure: The major parts of the delivery. * comments: Metadata for the export (query, export time...), prefixed with # * header: The export field names * content: The export content itself
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
    public javax.ws.rs.core.StreamingOutput exportFields(String query, List<String> fields, Boolean dryrun, List<String> structure) throws ServiceException {
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
                map(field -> Arrays.asList(field.split(", *"))).
                flatMap(Collection::stream).
                collect(Collectors.toCollection(LinkedHashSet::new));

        if (!allowedAviserExportFields.containsAll(eFields)) {
            eFields.removeAll(allowedAviserExportFields);
            throw new InvalidArgumentServiceException(
                    "Error: Unsupported export fields " + eFields + ": . " +
                    "Valid fields are " + allowedAviserExportFields);
        }
        Set<SolrBridge.STRUCTURE> structureSet = SolrBridge.STRUCTURE.valueOf(structure);
        log.debug(String.format(Locale.ENGLISH,
                                "Exporting fields %s with dryrun=%b and structure=%s for query '%s'",
                                eFields, dryrun, structureSet.toString(), query));
        try{
            httpServletResponse.setHeader("Content-Disposition",
                                          "inline; filename=\"mediestream_" + getCurrentTimeISO() + ".csv\"");
            return SolrBridge.export(query, eFields, structureSet);
        } catch (Exception e){
            throw handleException(e);
        }
    }
    private synchronized static String getCurrentTimeISO() {
        return SIMPLE_ISO.format(new Date());
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

}
