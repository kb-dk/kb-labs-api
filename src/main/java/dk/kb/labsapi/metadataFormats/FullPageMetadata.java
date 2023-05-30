package dk.kb.labsapi.metadataFormats;

import dk.kb.labsapi.config.ServiceConfig;
import org.apache.solr.client.solrj.response.QueryResponse;

import java.io.IOException;
import java.net.URL;

/**
 * Object containing metadata about a full page from a newspaper.
 * The variables of the object are used when querying the image server containing the newspapers.
 */
public class FullPageMetadata extends BasicMetadata {
    private String pageUUID;



    public FullPageMetadata(String pageUUID, Long pageWidth, Long pageHeight) throws IOException {
        String realUUID = pageUUID.replace("doms_aviser_page:uuid:", "");
        this.pageUUID = realUUID;
        this.pageHeight = pageHeight;
        this.pageWidth = pageWidth;
        this.imageURL = createFullPageLink();
    }

    public void setPageUUID(String pageUUID) {
        this.pageUUID = pageUUID;
    }

    public String getPageUUID() {
        return pageUUID;
    }

    @Override
    public void setImageURL(String imageURL) throws IOException {
        this.imageURL = createFullPageLink();
    }


    // Helper methods

    /**
     * Construct link to pages from metadata.
     * @return a URL to the page described in the input metadata.
     */
    public URL createFullPageLink() throws IOException {
        String baseURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");
        String baseParams = "&CVT=jpeg";
        String pageUuid = this.pageUUID + ".jp2";
        String prePageUuid = "/" + pageUuid.charAt(0) + "/" + pageUuid.charAt(1) + "/" + pageUuid.charAt(2) + "/" + pageUuid.charAt(3) + "/";
        String region = "&RGN=1,1,1,1";

        return new URL(baseURL+prePageUuid+pageUuid+baseParams);

    }
}
