package dk.kb.labsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Object that contains metadata for single illustrations from historical newspapers.
 * All integer variables are stored as <a href="https://www.leadtools.com/help/sdk/v22/dh/ft/altoxmlmeasurementunit.html">inch1200</a> numbers.
 * The variables of the object are used when querying the image server containing the newspapers.
 * The metadata gets used to extract single illustrations instead of complete pages.
 * <br/><br/>
 * Each object contains the following information as inch1200 values:
 * <ul>
 *     <li>pageUUID: Unique ID for each page</li>
 *     <li>pageWidth: Width of the newspaper page</li>
 *     <li>pageHeight: Height of the newspaper page</li>
 *     <li>id: ID for each illustration</li>
 *     <li>x: x value for illustration</li>
 *     <li>y: y value for illustration</li>
 *     <li>w: width of illustration</li>
 *     <li>x: height of illustration</li>
 * </ul>
 */
public class IllustrationMetadata {
    private static final Logger log = LoggerFactory.getLogger(IllustrationMetadata.class);
    private String pageUUID;
    private int pageWidth;
    private int pageHeight;
    private String id;
    private int x;
    private int y;
    private int w;
    private int h;
    static final Pattern illustrationPattern = Pattern.compile("id=(\\S*),x=(\\d*),y=(\\d*),w=(\\d*),h=(\\d*),doms_aviser_page:uuid:(\\S*),(\\d*),(\\d*)");

    public IllustrationMetadata(){
    }

    // Setter
    public void setData(String input) {
        Matcher m = illustrationPattern.matcher(input);
        if (m.matches()) {
            this.id = m.group(1);
            this.x = Integer.parseInt(m.group(2));
            this.y = Integer.parseInt(m.group(3));
            this.w = Integer.parseInt(m.group(4));
            this.h = Integer.parseInt(m.group(5));
            this.pageUUID = m.group(6);
            this.pageWidth = convertPixelsToInch1200(Integer.parseInt(m.group(7)));
            this.pageHeight = convertPixelsToInch1200(Integer.parseInt(m.group(8)));
        } else {
            log.error("Regex matching failed. Please check that input and pattern aligns.");
        }
    }

    // Getters
    public String getPageUUID(){
        return pageUUID;
    }
    public int getPageWidth() {
        return pageWidth;
    }
    public int getPageHeight() {
        return pageHeight;
    }
    public String getId() {
        return id;
    }
    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }
    public int getW() {
        return w;
    }
    public int getH() {
        return h;
    }

    /**
     * Convert values from pixel to <a href="https://www.leadtools.com/help/sdk/v22/dh/ft/altoxmlmeasurementunit.html">inch1200</a> using known DPI.
     * @param pixelValue to convert to inch1200 value.
     * @return the inch1200 equal of the input pixel value.
     */
    private int convertPixelsToInch1200(int pixelValue){
        final int DPI = 300;
        return pixelValue / DPI * 1200;
    }
}
