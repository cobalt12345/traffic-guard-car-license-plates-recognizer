package den.tal.traffic.guard;

import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.Geometry;
import com.amazonaws.services.rekognition.model.Point;
import com.amazonaws.services.rekognition.model.TextDetection;
import den.tal.traffic.guard.settings.Params;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@Slf4j
public class ImageFragmentExtractorTest {

    private static final float SCALE = 4;
    private static final File SOURCE_IMAGE = new File("src/test/resources/IMG_3027.jpg");
    private static final File TARGET_FOLDER = new File("build");
    private BufferedImage sourceImage;
    private ImageFragmentExtractor fragmentExtractor;
    private TextDetection detectedText;

    @Before
    public void setUp() throws IOException {
        fragmentExtractor = new ImageFragmentExtractor(SCALE);
        sourceImage = ImageIO.read(SOURCE_IMAGE);
        detectedText = new TextDetection();
        detectedText.setDetectedText("E642YH36");
        detectedText.setType("LINE");
        detectedText.setId(0);
        detectedText.setConfidence(95.72698f);
        Geometry geometry = new Geometry();
        BoundingBox boundingBox = new BoundingBox();
        boundingBox.setWidth(0.11053524f);
        boundingBox.setHeight(0.07325277f);
        boundingBox.setLeft(0.12877458f);
        boundingBox.setTop(0.74672025f);
        geometry.setBoundingBox(boundingBox);
        Point p0 = new Point();
        p0.setX(0.13589984f);
        p0.setY(0.74672025f);
        Point p1 = new Point();
        p1.setX(0.23930983f);
        p1.setY(0.7887888f);
        Point p2 = new Point();
        p2.setX(0.23218457f);
        p2.setY(0.81997305f);
        Point p3 = new Point();
        p3.setX(0.12877458f);
        p3.setY(0.7779045f);
        geometry.setPolygon(Arrays.asList(p0, p1, p2, p3));
        detectedText.setGeometry(geometry);
    }

    @Test
    public void extractFragmentTest() throws IOException {
        BufferedImage fragmentImage = fragmentExtractor.extractFragment(detectedText.getGeometry().getBoundingBox(),
                sourceImage);

        writeImage(fragmentImage, "_x_");
    }

    @Test
    public void boundFragment() throws IOException {
        BufferedImage imageWithBound = fragmentExtractor.boundFragment(detectedText.getGeometry(), sourceImage,
                true);

        writeImage(imageWithBound, "_bounded");
    }

    private void writeImage(BufferedImage image, String fileNameSuffix) throws IOException {
        String[] splittedSourceImageFileName = SOURCE_IMAGE.getName().split("\\.");
        assertEquals("Source image file name must have an extension!", splittedSourceImageFileName.length, 2);

        final File targetImageFile = new File(splittedSourceImageFileName[0]
                .concat(fileNameSuffix).concat(Float.toString(SCALE)).concat(".")
                    .concat(splittedSourceImageFileName[1]));

        targetImageFile.delete();
        if (!TARGET_FOLDER.exists()) {
            Files.createDirectory(TARGET_FOLDER.toPath());
        }
        ImageIO.write(image, Params.FORMAT_NAME.toString(), Paths.get(TARGET_FOLDER.toString(),
                targetImageFile.toString()).toFile());
    }
}
