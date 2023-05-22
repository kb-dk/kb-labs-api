package dk.kb.labsapi.metadataFormats;

/**
 * Object containing basic metadata for newspaper pages.
 * Each object contains the following information:
 *   <ul>
 *       <li>pageUUID: Unique ID for each page</li>
 *       <li>pageWidth: Width of the newspaper page</li>
 *       <li>pageHeight: Height of the newspaper page</li>
 *   </ul>
 */
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