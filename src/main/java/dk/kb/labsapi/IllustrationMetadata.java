package dk.kb.labsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dk.kb.labsapi.ImageExtractor.getIllustrationsList;

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
            this.pageWidth = Integer.parseInt(m.group(7));
            this.pageHeight = Integer.parseInt(m.group(8));
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
}
