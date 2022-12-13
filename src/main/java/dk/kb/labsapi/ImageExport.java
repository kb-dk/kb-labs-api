package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.GroupParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports images from text queries to Solr below mediestream.
 * Images are defined by alto boxes.
 */
public class ImageExport {
    private static final Logger log = LoggerFactory.getLogger(ImageExport.class);
    public static final int pageSize = SolrExport.getInstance().pageSize;

    /**
     * Get link to image from newspaper page with given query present in text
     *
     * @param query     to search for
     * @param startTime
     * @param endTime
     * @param max       number of images to return
     * @return urls to images
     */
    static public ByteArrayOutputStream getImageFromTextQuery(String query, int startTime, int endTime, int max) throws IOException {
        // Query Solr
        QueryResponse response = illustrationSolrCall(query, startTime, endTime, max);
        // Get illustration metadata
        List<IllustrationMetadata> illustrationMetadata = getMetadataForIllustrations(response);
        // Get illustration URLS
        List<URL> illustrationUrls = createLinkForAllIllustrations(illustrationMetadata);
        // Get illustrations as bytearrays
        List<byte[]> illustrationsAsByteArrays = downloadAllIllustrations(illustrationUrls);
        // Create bytearray of all images as zipArray
        ByteArrayOutputStream allImagesAsJpgs = byteArraysToZipArray(illustrationsAsByteArrays);

        return allImagesAsJpgs;

    }

    /**
     * Call Solr for input query and return fields needed to extract images from pages in the query.
     *
     * @param query     to call Solr with.
     * @param startTime
     * @param endTime
     * @param max       number of results to return
     * @return a response containing specific metadata used to locate illustration on pages. The fields returned are the following: <em>pageUUID, illustration, page_width, page_height</em>
     */
    static public QueryResponse illustrationSolrCall(String query, int startTime, int endTime, int max){
        // Construct solr query
        String filter = "recordBase:doms_aviser_page AND py:[* TO 1880]";
        // TODO: Filter has to be applied differently. Currently it adds a second py filter if users adds that to their query and that creates an error
        // TODO: SET py filter from method arguments startTime and endTime from API query
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFilterQueries(filter);
        solrQuery.setFields("pageUUID, illustration, page_width, page_height");
        solrQuery.setRows(Math.min(max == -1 ? Integer.MAX_VALUE : max, pageSize));
        // TODO: Implement -1 to return all
        solrQuery.setFacet(false);
        solrQuery.setHighlight(false);
        solrQuery.set(GroupParams.GROUP, false);

        QueryResponse response;
        try {
            response = SolrExport.getInstance().callSolr(solrQuery);
        } catch (Exception e) {
            String message = "Error calling Solr for query: " + query;
            log.warn(message, e);
            throw new RuntimeException(message);
        }
        return response;
    }

    /**
     * Get metadata values for all illustrations from query.
     * The returned list contains an object of metadata from each single illustration.
     * These inner objects contain the following values in order: String id, int x, int y, int w, int h, string pageUUID, int pageWidth and int pageHeight.
     * X and Y are coordinates, w = width and h = height. pageUUID, pageWidth and pageHeight are related to the page, which the illustration has been extracted from
     * @return a list of objects consisting of the id, x, y, w, h, pageUUID, pageWidth and pageHeight values that are used to extract illustrations.
     */
    static public List<IllustrationMetadata> getMetadataForIllustrations(QueryResponse solrResponse) {
        List<IllustrationMetadata> illustrations = new ArrayList<>();
        // Parse result from query and save into a list of strings
        List<String> illustrationList = getIllustrationsList(solrResponse);
        // Map strings to illustration metadata
        for (String s : illustrationList) {
            // Create Illustration metadata object
            IllustrationMetadata singleIllustration = new IllustrationMetadata();
            singleIllustration.setData(s);
            // Add object to list of object
            illustrations.add(singleIllustration);
        }
        return illustrations;
    }

    /**
     * Parse Solr QueryResponse of newspaper pages into list of individual illustrations.
     * @param solrResponse to extract illustrations from.
     * @return a list of strings. Each string contains metadata for a single illustration from the input query response.
     */
    static public List<String> getIllustrationsList(QueryResponse solrResponse) {
        SolrDocumentList responseList = solrResponse.getResults();
        List<String> illustrationList = new ArrayList<>();
        // Extract metadata from documents in solr response
        for (int i = 0; i<responseList.size(); i++) {
            String pageUUID = responseList.get(i).getFieldValue("pageUUID").toString();
            long pageWidth = (long) responseList.get(i).getFieldValue("page_width");
            long pageHeight = (long) responseList.get(i).getFieldValue("page_height");
            List<String> illustrations = (List<String>) responseList.get(i).getFieldValue("illustration");
            // Check if illustrations are present. If not, continue to next SolrDocument in list
            if (illustrations == null) {
                continue;
            }
            // Create metadata string for each illustration
            for (int j = 0; j< illustrations.size(); j++){
                illustrations.set(j, illustrations.get(j) + "," + pageUUID + "," + pageWidth + "," + pageHeight);
            }
            illustrationList.addAll(illustrations);
        }
        return illustrationList;
    }

    /**
     * Create a link for each illustration in the given metadataList
     * @param metadataList a list of objects consisting of metadata used to construct links to the image server
     * @return a list of URLs pointing to the imageserver, that contains the illustrations.
     */
    public static List<URL> createLinkForAllIllustrations(List<IllustrationMetadata> metadataList) throws IOException {
        List<URL> illustrationUrls = new ArrayList<>();

        for (int i = 0; i< metadataList.size(); i++){
            illustrationUrls.add(createIllustrationLink(metadataList.get(i)));
        }
        return illustrationUrls;
    }

    /**
     * Construct link to illustration from metadata.
     * @param ill is a class containing metadata for an illustration
     * @return a URL to the illustration described in the input metadata
     */
    public static URL createIllustrationLink(IllustrationMetadata ill) throws IOException {
        String baseURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");
        String baseParams = "&CVT=jpeg";
        String pageUuid = ill.getPageUUID();
        String prePageUuid = "/" + pageUuid.charAt(0) + "/" + pageUuid.charAt(1) + "/" + pageUuid.charAt(2) + "/" + pageUuid.charAt(3) + "/";
        // TODO: Should some encoding happen here, so that we are not using commas directly in URL?
        String region = calculateIllustrationRegion(ill.getX(), ill.getY(), ill.getW(), ill.getH(), ill.getPageWidth(), ill.getPageHeight());

        URL finalUrl = new URL(baseURL+prePageUuid+pageUuid+region+baseParams);
        return finalUrl;
    }

    /**
     * Calculate X & W coordinates, width and height for region parameter. Converts input pixel values to fractions of image size.
     * The image server containing the images uses the <a href="https://iipimage.sourceforge.io/documentation/protocol/">Internet Imaging Protocol</a>.
     * @return a region string that is ready to be added to an IIP query
     */
    public static String calculateIllustrationRegion(int x, int y, int w, int h, int width, int height){
        // Some illustrations have X and Y values greater than the width and height of the page.
        // Currently, these values are turned into zeroes and are therefore returning an incorrect image.
        // TODO: Ideally they should return the correct image (if that exists?) or nothing at all. LOOK INTO ALTO FILES
        float calculatedX = (float) x / (float) Math.max(width, x);
        float calculatedY = (float) y / (float) Math.max(height, y);
        float calculatedW = (float) w / (float) Math.max(w, width);
        float calculatedH = (float) h / (float) Math.max(h, height);
/*
        if (calculatedX >= 1.0F){
            calculatedX = 0;
        }
        if (calculatedY >= 1.0F){
            calculatedY = 0;
        }

 */
        // Fraction calculation from: https://math.hws.edu/graphicsbook/c2/s1.html
        // newX = newLeft + ((oldX - oldLeft) / (oldRight - oldLeft)) * (newRight - newLeft))
        // newY = newTop + ((oldY - oldTop) / (oldBottom - oldTop)) * (newBottom - newTop)
        return "&RGN="+calculatedX+","+calculatedY+","+calculatedW+","+calculatedH;//+"&WID="+width+"&HEI="+height;
    }

    public static List<byte[]> downloadAllIllustrations(List<URL> illustrationUrls) throws IOException {
        List<byte[]> allIllustrations = new ArrayList<>();

        for (int i = 0; i<illustrationUrls.size(); i++) {
            byte[] illustrationAsByteArray = downloadSingleIllustration(illustrationUrls.get(i));
            allIllustrations.add(illustrationAsByteArray);
        }
        return allIllustrations;
    }


    public static byte[] downloadSingleIllustration(URL url) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] illustrationAsByteArray = new byte[0];
        InputStream is = null;
        try {
            is = url.openStream();
            byte[] bytes = new byte[4096];
            int n;

            while ((n = is.read(bytes)) > 0) {
                baos.write(bytes, 0, n);
            }
            illustrationAsByteArray = baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to download illustration from " + url + " while reading bytes");
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return illustrationAsByteArray;
    }

    public static ByteArrayOutputStream byteArraysToZipArray(List<byte[]> illustrationsAsByteArrays) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        int count = 0;
        try {
            // Add byte arrays from input to zip
            for (int i = 0; i < illustrationsAsByteArrays.size(); i++) {
                addToZipStream(illustrationsAsByteArrays.get(i), "illustration_" + count + ".jpeg", zos);
                count += 1;
            }
            // Close the zip output stream
            zos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos;
    }

    private static void addToZipStream(byte[] data, String fileName, ZipOutputStream zos) throws IOException{
        // Create a buffer to read the data into
        byte[] buffer = new byte[1024];
        // Create a new zip entry with the file's name
        ZipEntry ze = new ZipEntry(fileName);
        // Add the zip entry to the zip output stream
        zos.putNextEntry(ze);
        // Read the data into the buffer
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        int len;
        while ((len = bais.read(buffer)) > 0) {
            // Write the buffer to the zip output stream
            zos.write(buffer, 0, len);
        }
        // Close the zip entry
        zos.closeEntry();
        // Close the byte array input stream
        bais.close();
    }
}
