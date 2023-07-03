package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.labsapi.metadataFormats.BasicMetadata;
import dk.kb.labsapi.metadataFormats.FullPageMetadata;
import dk.kb.labsapi.metadataFormats.IllustrationMetadata;
import dk.kb.util.webservice.exception.InternalServiceException;
import dk.kb.util.webservice.exception.InvalidArgumentServiceException;
import dk.kb.util.yaml.YAML;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.GroupParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static dk.kb.labsapi.SolrExport.STRUCTURE.content;

/**
 * Exports images from text queries to Solr below mediestream.
 * Images are defined by alto boxes.
 */
public class ImageExport {
    private static final Logger log = LoggerFactory.getLogger(ImageExport.class);
    public static int pageSize;
    private static ImageExport instance;
    private final String ImageExportService;
    private static int partitionSize;

    /**
     * Fields used for generating CSV metadata for each image exported.
     */
    private static List<String> CSVFIELDS = new ArrayList<>();
    private static int minAllowedStartYear;
    private static int maxAllowedEndYear;
    private static int maxExport;
    private static int defaultExport;
    static final Pattern pagePattern = Pattern.compile("doms_aviser_page:uuid:(\\S*)");

    public static ImageExport getInstance() {
        instance = new ImageExport();
        return instance;
    }

    private ImageExport() {
        YAML conf;
        try {
            conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser");
        } catch (Exception e) {
            log.error("The configuration sub map '.labsapi.aviser' was not defined");
            ImageExportService = null;
            return;
        }
        pageSize = conf.getInteger(".solr.pagesize", 500);
        ImageExportService = conf.getString(".imageserver.url") + (conf.getString(".imageserver.url").endsWith("/") ? "" : "/");
        minAllowedStartYear = conf.getInteger(".imageserver.minYear");
        maxAllowedEndYear = conf.getInteger(".imageserver.maxYear");
        maxExport = conf.getInteger(".imageserver.maxExport");
        defaultExport = conf.getInteger(".imageserver.defaultExport");
        CSVFIELDS = conf.getList(".imageserver.metadataFields");
        partitionSize = conf.getInteger(".imageserver.csvPartitionSize");
        log.info("Created ImageExport that exports images from this server: '{}'", ImageExportService);
    }

    /**
     * Create map of metadata for query.
     * @param query used to query solr.
     * @param startYear for the given query.
     * @param endYear for query.
     * @return a map of metadata used to provide a metadata file.
     */
    public static Map<String, Object> makeMetadataMap(String query, Integer startYear, Integer endYear) {
        Date date = new Date();

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("title", "Metadata for image extraction from the Danish Royal Librarys newspaper API.");
        metadataMap.put("extraction_time", date.toString());
        metadataMap.put("query", query);
        metadataMap.put("query_start_year", setUsableStartYear(startYear));
        metadataMap.put("query_end_year", setUsableEndYear(endYear));
        metadataMap.put("license", "Creative Commons Public Domain Mark 1.0 License.");

        return metadataMap;
    }

    /**
     * Create base solr query.
     * @param query to search for.
     * @param startYear sets the start of period range.
     * @param endYear sets the end of period range.
     * @return a solrQuery object that can be extended before querying.
     */
    private SolrQuery createSolrQuery(String query, Integer startYear, Integer endYear) throws IOException {
        validateQueryParams(startYear, endYear);
        int usableStartYear = setUsableStartYear(startYear);
        int usableEndYear = setUsableEndYear(endYear);

        // Construct solr query with filter
        String filter = "recordBase:doms_aviser AND py:[" + usableStartYear + " TO "+ usableEndYear + "]";
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.addFilterQuery(filter);
        solrQuery.setQuery(query);
        solrQuery.setFacet(false);
        solrQuery.setHighlight(false);
        solrQuery.set(GroupParams.GROUP, false);

        return solrQuery;
    }

    /**
     * Validate query parameters that are to be used for image extraction.
     * @param startYear of timespan for query.
     * @param endYear of timespan for query.
     */
    private void validateQueryParams(Integer startYear, Integer endYear) {
        // Check start and end times
        int usableStartYear = setUsableStartYear(startYear);
        int usableEndYear = setUsableEndYear(endYear);
        log.debug("Usable start time is: " + usableStartYear + " and usable end time is: " + usableEndYear );
        if (usableStartYear > usableEndYear){
            log.error("The variable startYear is greater than endYear, which is not allowed. Please make startYear less than endYear.");
            throw new InvalidArgumentServiceException("The variable startYear is greater than endYear, which is not allowed. Please make startYear less than endYear.");
        }
    }

    /**
     * Evaluate that startYear is allowed in configuration of service.
     * @param startYear the chosen year for start of query.
     * @return startYear if allowed else return default start year.
     */
    public static int setUsableStartYear(int startYear){
         if (startYear < minAllowedStartYear){
             log.info("Using startYear " + minAllowedStartYear);
             return minAllowedStartYear;
         } else {
             log.info("using startYear " + startYear);
             return startYear;
         }
    }

    /**
     * Evaluate that endYear is allowed in configuration of service.
     * @param endYear the chosen year for end of query.
     * @return endYear if allowed else return default end year.
     */
    public static int setUsableEndYear(int endYear){
         if (endYear > maxAllowedEndYear){
             log.info("Using endYear " + maxAllowedEndYear);
             return maxAllowedEndYear;
         } else {
             log.info("Using endYear" + endYear);
             return endYear;
         }
    }

    /**
     * Remove prefix <em>doms_aviser_page:uuid</em> from pageUUID.
     * @param pageUUID to convert.
     * @return correct pageUUID without prefix.
     */
    String convertPageUUID(String pageUUID){
         String correctUUID = "";
         Matcher m = pagePattern.matcher(pageUUID);
         if (m.matches()){
             correctUUID = m.group(1);
         } else {
             log.warn("pageUUID conversion failed for string: '" + pageUUID + "'.");
             throw new InternalServiceException("pageUUID conversion failed.");
         }
         return correctUUID;
    }

    /**
     * Download an illustration from given URL and return it as a byte array.
     * @param url pointing to the image to download.
     * @return downloaded image as byte array.
     */
     public byte[] downloadSingleIllustration(URL url) {
        try {
            return IOUtils.toByteArray(url);
        }  catch (IOException e) {
            log.error("Failed to download illustration from " + url + " while reading bytes");
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a metadata file to the created zip file before images are added to the file.
     * @param metadataMap containing metadata for the given solr query.
     * @param zos ZipOutputStream to write the metadata file to.
     */
    public void addMetadataFileToZip(Map<String, Object> metadataMap, ZipOutputStream zos) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter()).without(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        ZipEntry ze = new ZipEntry("metadata.json");
        zos.putNextEntry(ze);
        writer.writeValue(zos, metadataMap);
        zos.closeEntry();
    }

    /**
     * Add a streaming output containing data structured as csv to a zipped output stream.
     *
     * @param csvStream to include in zip stream.
     * @param zos       is the zip stream which the csv file gets added to.
     */
    public void addCsvMetadataFileToZip(Stream<StreamingOutput> csvStream, ZipOutputStream zos) throws IOException {
        ZipEntry ze = new ZipEntry("imageMetadata.csv");
        zos.putNextEntry(ze);

        //OutputStream nonCloser = Utils.getNonCloser(zos);
        csvStream.forEach(csv -> Utils.safeStreamWrite(csv, zos));
    }

    /**
     * Add the content of a byte array to a given zip output stream.
     * @param data to write to zip stream as individual zip entry.
     * @param fileName given to data input in the zip stream.
     * @param zos ZipOutputStream that gets streamed to.
     */
    private void addToZipStream(byte[] data, String fileName, ZipOutputStream zos) throws IOException{
        // Create a buffer to read into
        byte[] buffer = new byte[1024];
        // Create a zip entry with individual filename
        ZipEntry ze = new ZipEntry(fileName);
        // Add the zip entry to the zip output stream
        zos.putNextEntry(ze);
        // Write data to zipEntry
        zos.write(data);
        // Close the zip entry
        zos.closeEntry();
        zos.flush();
    }

    /************************************* Streaming implementation **********************************************/

    /**
     * Get images of newspaper pages with given query present in text.
     * @param query     to search for.
     * @param startYear is the earliest boundary for the query. Boundaries are inclusive.
     * @param endYear   is the latest boundary for the query. Boundaries are inclusive.
     * @param max       number of documents to fetch.
     * @param output    to write images to as one combined zip file.
     */
    public void exportFullpages(String query, Integer startYear, Integer endYear, Integer max, OutputStream output, String exportFormat) throws IOException {
        if (instance.ImageExportService == null) {
            throw new InternalServiceException("Illustration delivery service has not been configured, sorry");
        }
        // Query Solr
        SolrQuery finalQuery = fullpageSolrQuery(query, startYear, endYear);
        Stream<SolrDocument> docs = streamSolr(finalQuery);
        // Create metadata file, that has to be added to output zip
        Map<String, Object> metadataMap = makeMetadataMap(query, startYear, endYear);

        // Get fullPage metadata
        Set<String> uniqueUUIDs = docs
                .map(doc -> saveDeduplicationList(doc, new HashSet<>()))
                .filter(Objects::nonNull)
                .limit(max)
                .collect(Collectors.toSet());

        Stream<FullPageMetadata> pageMetadata = docs
                .map(doc -> getMetadataForFullPage(doc, uniqueUUIDs))
                .filter(Objects::nonNull)
                .limit(max);

        // Create csv stream containing metadata from query
        Stream<StreamingOutput> fullCsv = Stream.concat(Stream.of(createHeaderForCsvStream()), streamCsvOfUniqueUUIDsMetadata(uniqueUUIDs, max));

        // Streams pages from URL to zip file with all illustrations
        int count = createZipOfImages(pageMetadata, output, metadataMap, fullCsv, exportFormat);
        log.info("Found: '" + count + "' unique UUIDs in query");

    }

    /**
     * Get illustrations from newspaper pages where given query is present in text.
     * @param query     to search for.
     * @param startYear is the earliest boundary for the query. Boundaries are inclusive.
     * @param endYear   is the latest boundary for the query. Boundaries are inclusive.
     * @param max       number of documents to fetch.
     * @param output    to write images to as one combined zip file.
     */
    public void exportIllustrations(String query, Integer startYear, Integer endYear, Integer max, OutputStream output, String exportFormat) throws IOException {
        if (instance.ImageExportService == null) {
            throw new InternalServiceException("Illustration delivery service has not been configured, sorry");
        }
        // Query Solr
        SolrQuery finalQuery = illustrationSolrQuery(query, startYear, endYear, max);
        Stream<SolrDocument> docs = streamSolr(finalQuery);
        // Create metadata file, that has to be added to output zip
        Map<String, Object> metadataMap = makeMetadataMap(query, startYear, endYear);

        // Create metadata objects
        HashSet<String> uniqueUUIDs = new HashSet<>();
        Stream<IllustrationMetadata> illustrationMetadata = docs
                .flatMap(doc -> getMetadataForIllustrations(doc, uniqueUUIDs)
                .limit(max));

        // Create csv stream containing metadata from query
        StreamingOutput csvHeader = createHeaderForCsvStream();
        StreamingOutput csvStream = SolrExport.getInstance().export(query, Set.copyOf(CSVFIELDS),(long) max, SolrExport.STRUCTURE.DEFAULT , SolrExport.EXPORT_FORMAT.csv );

        // Streams illustration from URL to zip file with all illustrations
        int count = createZipOfImages(illustrationMetadata, output, metadataMap, (Stream<StreamingOutput>) csvStream, exportFormat);
        log.info("Exported: '{} unique UUIDs from query: '{}' with startYear: {} and endYear: {}", count, query, startYear, endYear);
    }

    /**
     * Construct Solr query for input.
     * @param query to query solr with
     * @param startYear is the earliest boundary for the query. Boundaries are inclusive.
     * @param endyear is the latest boundary for the query. Boundaries are inclusive.
     * @return a solr query used to deliver images of all pages. The fields asked for are the following: <em>pageUUID, page_width and page_height</em>
     */
    public SolrQuery fullpageSolrQuery(String query, Integer startYear, Integer endyear) throws IOException {
        // Construct solr query with filter
        SolrQuery solrQuery = createSolrQuery(query, startYear, endyear);
        solrQuery.setFields("pageUUID, page_width, page_height");

        return solrQuery;
    }

    /**
     * Construct Solr query for input.
     * @param query     to query Solr with.
     * @param startYear startYear is the earliest boundary for the query. Boundaries are inclusive.
     * @param endYear   endYear is the latest boundary for the query. Boundaries are inclusive.
     * @param max       number of results to return
     * @return a response containing specific metadata used to locate illustration on pages. The fields asked for are the following: <em>pageUUID, illustration, page_width, page_height</em>
     */
    public SolrQuery illustrationSolrQuery(String query, Integer startYear, Integer endYear, int max) throws IOException{
        // Construct solr query with filter
        SolrQuery solrQuery = createSolrQuery(query, startYear, endYear);
        solrQuery.addFilterQuery("illustration: [* TO *]");
        solrQuery.setFields("pageUUID, illustration, page_width, page_height");

        return solrQuery;
    }

    /**
     * Create stream of solr document from query.
     * @param query to create stream from.
     * @return a stream of SolrDocuments representing newspaper article hits.
     */
    public Stream<SolrDocument> streamSolr(SolrQuery query){
        SolrBase base = new SolrBase(".labsapi.aviser");
        Stream<SolrDocument> docs = base.streamSolr(query);
        return docs;
    }

    /**
     * Get metadata values for a given SolrDocument.
     * The returned object contains metadata about a single page.
     * @return an object containing metadata from a single page. metadata values are: pageUUID, pageWidth and pageHeight.
     */
    public FullPageMetadata getMetadataForFullPage(SolrDocument doc, Set<String> uniqueUUIDs) {
        FullPageMetadata page = null;

        // Extract metadata from SolrDocument
        String pageUUID = doc.getFieldValue("pageUUID").toString();
        String correctUUID = convertPageUUID(pageUUID);
        if (!uniqueUUIDs.add(correctUUID)){
            return null;
        }
        try {
            page = new FullPageMetadata(doc.get("pageUUID").toString(), (Long) doc.get("page_width"), (Long) doc.get("page_height"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return page;
    }

    /**
     * Get a stream of IllustrationMetadata from a given SolrDocument
     * The returned stream contains metadata about each single illustration in the given SolrDocument.
     * IllustrationMetadata objects contain the following values: String id, int x, int y, int w, int h, string pageUUID, int pageWidth, int pageHeight and imageURL.
     * X and Y are coordinates, w = width and h = height. pageUUID, pageWidth and pageHeight are related to the page, which the illustration has been extracted from and imageURL is the URL where the illustration is available.
     * @return a list of metadata objects consisting of the id, x, y, w, h, pageUUID, pageWidth and pageHeight values that are used to extract illustrations.
     */
    public Stream<IllustrationMetadata> getMetadataForIllustrations(SolrDocument doc, HashSet<String> uniqueUUIDs) {
        // TODO: This endpoint still returns some odd illustrations, which are clearly not illustrations nut flaws in the illustration boxes. However it works and these illustrations can be filtered away later by filtering small hights away

        // Extract metadata from SolrDocument
        String pageUUID = doc.getFieldValue("pageUUID").toString();
        String correctUUID = convertPageUUID(pageUUID);
        if (!uniqueUUIDs.add(correctUUID)){
            return null;
        }
        long pageWidth = (long) doc.getFieldValue("page_width");
        long pageHeight = (long) doc.getFieldValue("page_height");
        List<String> illustrations = (List<String>) doc.getFieldValue("illustration");
        // Check if illustrations are present. If not, continue to next SolrDocument in list
        if (illustrations == null) {
            return Stream.empty();
        }

        return illustrations.stream().map(metadata -> new IllustrationMetadata(metadata, correctUUID, pageWidth, pageHeight));
    }

    /**
     * Create ZIP file of images created from metadata objects.
     *
     * @param imageMetadata used to stream URLS from and construct filenames.
     * @param output        stream which holds the outputted zip file.
     * @param metadataMap   which delivers overall information on the export.
     * @param exportFormat  determines what kind of export that are to be done. Supports either "illustrations" or "fullPage".
     */
    public int createZipOfImages(Stream<? extends BasicMetadata> imageMetadata, OutputStream output, Map<String, Object> metadataMap, Stream <StreamingOutput> fullCsv, String exportFormat) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(output);
        zos.setLevel(Deflater.NO_COMPRESSION);

        // Add metadata file to zip
        try {
            addMetadataFileToZip(metadataMap, zos);
            addCsvMetadataFileToZip(fullCsv, zos);
        } catch (Exception e) {
            String message = String.format(
                    Locale.ROOT,
                    "Exception adding metadata entry to ZIP with  %s illustrationMetadatas",
                    imageMetadata == null ? "null" : imageMetadata.count());
            log.warn(message, e);
            throw new IOException(message, e);
        }

        AtomicInteger count = new AtomicInteger();
        try {
            switch (exportFormat){
                case "illustrations":
                    imageMetadata.forEach(metadata -> exportImage(metadata, exportFormat, count, zos));
                    zos.close();
                    break;
                case "fullPage":
                    imageMetadata.forEach(metadata -> exportImage(metadata, exportFormat, count, zos));
                    zos.close();
                    break;
            }

        } catch (IOException e) {
            log.error("Error adding illustration to ZIP stream.");
            throw new IOException();
        }
        return count.intValue();
    }

    /**
     * Export to use for illustration extraction.
     * @param metadata to download image for.
     * @param exportFormat that determines export type.
     * @param count to construct filenames.
     * @param zos to deliver all images to.
     */
    public void exportImage(BasicMetadata metadata, String exportFormat, AtomicInteger count, ZipOutputStream zos){
        byte[] illustration = downloadSingleIllustration(metadata.getImageURL());
        String pageUuid = metadata.getPageUUID();
        try {
            if (exportFormat.equals("illustrations")) {
                addToZipStream(illustration, String.format(Locale.ROOT, "pageUUID_%s_" + exportFormat + "_%03d.jpeg", pageUuid, count.get()), zos);
                count.addAndGet(1);
            }
            if (exportFormat.equals("fullPage")){
                addToZipStream(illustration, String.format(Locale.ROOT, "pageUUID_%s_" + exportFormat + ".jpeg", pageUuid), zos);
            }
            count.addAndGet(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Create a stream of streaming outputs containing metadata for images in CSV-format from a set of unique IDs.
     * @param uniqueUUIDs to extract metadata for.
     * @param max number of IDs to include in each output in the stream.
     * @return a stream consisting of StreamingOutputs with a given size
     */
    public Stream<StreamingOutput> streamCsvOfUniqueUUIDsMetadata(Set<String> uniqueUUIDs, int max) {
        SolrExport csvExporter =  SolrExport.getInstance();
        Stream<List<String>> streamOfUuidLists = Utils.splitToLists(uniqueUUIDs.stream(), partitionSize);

        Stream<StreamingOutput> csvOutput = streamOfUuidLists.map(this::createUuidQuery)
                .map(query ->
                        csvExporter.export(query.getQuery(), Set.copyOf(CSVFIELDS), max, Collections.singleton(content), SolrExport.EXPORT_FORMAT.csv )
                    );

        return csvOutput;
    }



    /**
     * Create a header for a CSV file from CSVFIELDS property.
     * @return the CVS header as a streaming output.
     */
    public StreamingOutput createHeaderForCsvStream() {
        SolrExport csvExporter = new SolrExport();
        return csvExporter.export("", Set.copyOf(CSVFIELDS), 1, Collections.singleton(SolrExport.STRUCTURE.header), SolrExport.EXPORT_FORMAT.csv);
    }


    /**
     * Construct a solr query that returns hits from newspapers with PageUUIDs from the input list.
     * @param list of pageUUIDs to query solr with.
     * @return a SolrQuery containing all pageUUIDs from input list formatted correctly.
     */
    public SolrQuery createUuidQuery(List<String> list) {
        String query = list.stream().map(Object::toString)
                .map(s -> "pageUUID:" + s)
                .collect(Collectors.joining(" OR "));

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFacet(false);
        solrQuery.setHighlight(false);
        solrQuery.set(GroupParams.GROUP, false);

        return solrQuery;
    }


    // ********************** Utils ***********************************

    /**
     * Deduplicates pageUUIDs
     * @param doc Solrdocument which pageUUID gets extracted from
     * @param uniqueUUIDs is a hashset, used for looking up duplicates.
     * @return the pageUUID if unique.
     */
    public String saveDeduplicationList(SolrDocument doc, HashSet<String> uniqueUUIDs){
        String pageUUID = doc.getFieldValue("pageUUID").toString();
        if (uniqueUUIDs.add(pageUUID)){
            return pageUUID;
        } else {
            return null;
        }
    }
}
