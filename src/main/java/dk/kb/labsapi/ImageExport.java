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

/**
 * Exports images from text queries to Solr below mediestream.
 * Images are defined by alto boxes.
 */
public class ImageExport {
    private static final Logger log = LoggerFactory.getLogger(ImageExport.class);


    /**
     * Get link to image from newspaper page with given query present in text
     * @param query to search for
     * @param max number of images to return
     * @return urls to images
     */
    static public List<ByteArrayOutputStream> getImageFromTextQuery(String query, int max) throws IOException {
        // Query Solr
        QueryResponse response = illustrationSolrCall(query, max);
        // Get illustration metadata
        List<IllustrationMetadata> illustrationMetadata = getMetadataForIllustrations(response);
        // Get illustration URLS
        List<URL> illustrationUrls = createLinkForAllIllustrations(illustrationMetadata);

        // TODO: Return the image from each URL in illustrationUrls
        List<ByteArrayOutputStream> images = downloadAllIllustrations(illustrationUrls);

        return images;

    }

    /**
     * Call Solr for input query and return fields needed to extract images from pages in the query.
     * @param query to call Solr with.
     * @param max number of results to return
     * @return a response containing specific metadata used to locate illustration on pages. The fields returned are the following: <em>pageUUID, illustration, page_width, page_height</em>
     */
    static public QueryResponse illustrationSolrCall(String query, int max){
        // Construct solr query
        String filter = "recordBase:doms_aviser_page AND py:[* TO 1880]";
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFilterQueries(filter);
        solrQuery.setFields("pageUUID, illustration, page_width, page_height");
        solrQuery.setRows(max); // TODO: Implement -1 to return all
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
     * @return a list of strings. Each string contains metadata for a single illustration from the input jsonString.
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
        float calculatedX = (float) x / (float) width;
        float calculatedY = (float) y / (float) height;
        float calculatedW = (float) w / (float) width;
        float calculatedH = (float) h / (float) height;
        return "&RGN="+calculatedX+","+calculatedY+","+calculatedW+","+calculatedH;
    }

    public static List<ByteArrayOutputStream> downloadAllIllustrations(List<URL> illustrationUrls) throws IOException {
        List<ByteArrayOutputStream> allImages = new ArrayList<>();

        for (int i = 0; i<illustrationUrls.size(); i++) {
            ByteArrayOutputStream baos = downloadSingleIllustration(illustrationUrls.get(i));
            allImages.add(baos);
        }
        return allImages;
    }


    public static ByteArrayOutputStream downloadSingleIllustration(URL url) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = url.openStream()) {
            byte[] bytes = new byte[4096];
            int n;

            while ((n = is.read(bytes)) > 0) {
                baos.write(bytes, 0, n);
            }
        } catch (IOException e) {
            log.error("Failed to download illustration from " + url + " while reading bytes");
        }
        return baos;
    }


}
