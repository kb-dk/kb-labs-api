package dk.kb.labsapi.metadataFormats;

public class BasicMetadata {
    String pageUUID;
    Long pageWidth;
    Long pageHeight;

    public void setPageUUID(String pageUUID) {
        this.pageUUID = pageUUID;
    }

    public String getPageUUID() {
        return pageUUID;
    }

    public void setPageHeight(Long pageHeight) {
        this.pageHeight = pageHeight;
    }

    public void setPageWidth(Long pageWidth) {
        this.pageWidth = pageWidth;
    }
    public double getPageHeight() {
        return pageHeight;
    }
    public double getPageWidth() {
        return pageWidth;
    }



}
