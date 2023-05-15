package dk.kb.labsapi.metadataFormats;

import org.apache.solr.client.solrj.response.QueryResponse;

public class FullPageMetadata extends BasicMetadata {
    private String pageUUID;
    private double pageWidth;
    private double pageHeight;

    public FullPageMetadata(String pageUUID, double pageWidth, double pageHeight){
        this.pageUUID = pageUUID;
        this.pageHeight = pageHeight;
        this.pageWidth = pageWidth;
    }

    public void setPageHeight(double pageHeight) {
        this.pageHeight = pageHeight;
    }

    public void setPageWidth(double pageWidth) {
        this.pageWidth = pageWidth;
    }

    public void setPageUUID(String pageUUID) {
        this.pageUUID = pageUUID;
    }

    public double getPageHeight() {
        return pageHeight;
    }

    public double getPageWidth() {
        return pageWidth;
    }

    public String getPageUUID() {
        return pageUUID;
    }
}
