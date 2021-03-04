package den.tal.traffic.guard;

import com.amazonaws.services.rekognition.model.TextDetection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import den.tal.traffic.guard.settings.Params;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import static org.junit.Assert.*;

public class ImageFragmentExtractorTest {

    private static final int SCALE = 1;
    private static final File SOURCE_IMAGE = new File("IMG_3027.jpg");
    private static final File TEXT_DETECTION_JSON = new File("TextDetection.json");
    private BufferedImage sourceImage;
    private ImageFragmentExtractor fragmentExtractor;
    private TextDetection detectedText;

    @Before
    public void setUp() throws IOException {
        fragmentExtractor = new ImageFragmentExtractor(SCALE);
        sourceImage = ImageIO.read(SOURCE_IMAGE);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileReader fileReader = new FileReader(TEXT_DETECTION_JSON);
                JsonReader jsonReader = new JsonReader(fileReader)) {

            detectedText = gson.fromJson(jsonReader, TextDetection.class);
        }
    }

    @Test
    public void extractFragmentTest() throws IOException {
        BufferedImage fragmentImage = fragmentExtractor.extractFragment(detectedText.getGeometry().getBoundingBox(),
                sourceImage);

        String[] splittedSourceImageFileName = SOURCE_IMAGE.getName().split(".");
        assertTrue("Source image file name must have an extension!",
                splittedSourceImageFileName.length == 2);

        final File targetImageFile = new File(splittedSourceImageFileName[0]
                .concat("_x_").concat(Integer.toString(SCALE)).concat(splittedSourceImageFileName[1]));

        targetImageFile.delete();
        ImageIO.write(fragmentImage, Params.FORMAT_NAME.toString(), targetImageFile);
    }
}
