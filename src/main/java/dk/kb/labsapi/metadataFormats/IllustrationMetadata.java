package dk.kb.labsapi.metadataFormats;

import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.util.webservice.exception.InternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
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
public class IllustrationMetadata extends BasicMetadata {
    private static final Logger log = LoggerFactory.getLogger(IllustrationMetadata.class);
    private String id;
    private double x;
    private double y;
    private double w;
    private double h;
    static final Pattern oldIllustrationPattern = Pattern.compile("id=(\\S*),x=(\\d*),y=(\\d*),w=(\\d*),h=(\\d*),doms_aviser_page:uuid:(\\S*),(\\d*),(\\d*)");
    static final Pattern singleIllustrationPattern = Pattern.compile("id=(\\S*),x=(\\d*),y=(\\d*),w=(\\d*),h=(\\d*)");

    /**
     * Create metadata object from solr result. All values are obtained from a solr response.
     * @param illustrationString in the format: {@code id=ART88-1_SUB,x=2364,y=4484,w=652,h=100}.
     * @param pageUUID of the page, where the illustration exists.
     * @param pageWidth of the  entire page, where the illustration is present.
     * @param pageHeight of the  entire page, where the illustration is present.
     */
    public IllustrationMetadata(String illustrationString, String pageUUID, long pageWidth, long pageHeight)  {
        Matcher m = singleIllustrationPattern.matcher(illustrationString);
        if (m.matches()){
            this.id = m.group(1);
            this.x = Double.parseDouble(m.group(2));
            this.y = Double.parseDouble(m.group(3));
            this.w = Double.parseDouble(m.group(4));
            this.h = Double.parseDouble(m.group(5));
        } else {
            log.warn("Regex matching failed. Could not create IllustrationMetadata from illustrationString: '" + illustrationString + "'.");
            throw new InternalServiceException("Regex matching failed. Could not create IllustrationMetadata from illustrationString.");
        }
        this.pageUUID = pageUUID;
        this.pageHeight = (long) convertPixelsToInch1200((double) pageHeight);
        this.pageWidth = (long) convertPixelsToInch1200((double) pageWidth);
        try {
            this.imageURL = createIllustrationLink();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public IllustrationMetadata(){

    }

    // Getters
    public String getId() {
        return id;
    }
    public double getX() {
        return x;
    }
    public double getY() {
        return y;
    }
    public double getW() {
        return w;
    }
    public double getH() {
        return h;
    }

    @Override
    public void setImageURL(String imageURL) throws IOException {
        this.imageURL = createIllustrationLink();
    }

    // Helper methods

    /**
     * Convert values from pixel to <a href="https://www.leadtools.com/help/sdk/v22/dh/ft/altoxmlmeasurementunit.html">inch1200</a> using known DPI.
     * @param pixelValue to convert to inch1200 value.
     * @return the inch1200 equal of the input pixel value.
     */
    private double convertPixelsToInch1200(double pixelValue){
        final int DPI = 300;
        return pixelValue / DPI * 1200.0;
    }

    /**
     * Convert input <a href="https://www.leadtools.com/help/sdk/v22/dh/ft/altoxmlmeasurementunit.html">inch1200</a> value to corrosponding pixel value.
     * @param inch1200Value to convert.
     * @return pixel representation of input value.
     */
    private double convertInch1200ToPixels(double inch1200Value){
        final int DPI = 300;
        return inch1200Value * DPI / 1200.0;
    }

    /**
     * Construct link to illustration from metadata.
     * @param ill is a class containing metadata for an illustration.
     * @return a URL to the illustration described in the input metadata.
     */
    public URL createIllustrationLink() throws IOException {
        String baseURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");
        String baseParams = "&CVT=jpeg";
        String pageUuid = this.pageUUID + ".jp2";
        String prePageUuid = "/" + pageUuid.charAt(0) + "/" + pageUuid.charAt(1) + "/" + pageUuid.charAt(2) + "/" + pageUuid.charAt(3) + "/";
        String region = calculateIllustrationRegion(this.x, this.y, this.w, this.h, this.pageWidth, this.pageHeight);

        return new URL(baseURL+prePageUuid+pageUuid+region+baseParams);

    }

    /**
     * Calculate X & W coordinates, width and height for region parameter. Converts input <a href="https://www.leadtools.com/help/sdk/v22/dh/ft/altoxmlmeasurementunit.html">inch1200</a> values to fractions of image size.
     * The image server containing the images uses the <a href="https://iipimage.sourceforge.io/documentation/protocol/">Internet Imaging Protocol</a>.
     * @param x coordinate for individual illustration.
     * @param y coordinate for individual illustration.
     * @param w represents the width of the individual illustration.
     * @param h represents the height of the individual illustration.
     * @param width of page where the illustration is found.
     * @param height of page where the illustration is found.
     * @return a region string that is ready to be added to an IIP query.
     */
    public String calculateIllustrationRegion(double x, double y, double w, double h, double width, double height){
        // Fraction calculation from: https://math.hws.edu/graphicsbook/c2/s1.html
        // newX = newLeft + ((oldX - oldLeft) / (oldRight - oldLeft)) * (newRight - newLeft))
        // newY = newTop + ((oldY - oldTop) / (oldBottom - oldTop)) * (newBottom - newTop)
        // The one used here is simplified because it has to return a fraction between 0 and 1.
        double calculatedX = x / width;
        double calculatedY = y / height;
        double calculatedW = w / width;
        double calculatedH = h / height;

        double widthInPixels = convertInch1200ToPixels(w);

        return String.format(Locale.ROOT, "&WID=%d&RGN=%1.5f,%1.5f,%1.5f,%1.5f", (int) widthInPixels, calculatedX, calculatedY, calculatedW, calculatedH);
    }

}
