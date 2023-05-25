package dk.kb.labsapi.metadataFormats;

import org.apache.solr.client.solrj.response.QueryResponse;

/**
 * Object containing metadata about a full page from a newspaper.
 * The variables of the object are used when querying the image server containing the newspapers.
 */
public class FullPageMetadata extends BasicMetadata {
    private String pageUUID;


    public FullPageMetadata(String pageUUID, Long pageWidth, Long pageHeight){
        String realUUID = pageUUID.replace("doms_aviser_page:uuid:", "");
        this.pageUUID = realUUID;
        this.pageHeight = pageHeight;
        this.pageWidth = pageWidth;
    }

    public void setPageUUID(String pageUUID) {
        this.pageUUID = pageUUID;
    }


    public String getPageUUID() {
        return pageUUID;
    }
}
