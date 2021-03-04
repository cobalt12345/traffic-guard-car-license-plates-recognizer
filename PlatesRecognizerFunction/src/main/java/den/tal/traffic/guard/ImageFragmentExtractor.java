package den.tal.traffic.guard;

import com.amazonaws.services.rekognition.model.BoundingBox;
import lombok.AllArgsConstructor;

import java.awt.image.BufferedImage;

@AllArgsConstructor
public class ImageFragmentExtractor {

    private int scale = 1;

    public BufferedImage extractFragment(BoundingBox boundingBox, BufferedImage sourceImage) {
        int sourceImageWidth = sourceImage.getWidth();
        int sourceImageHeight = sourceImage.getHeight();
        int fragmentLeft = (int) (sourceImageWidth * boundingBox.getLeft().floatValue());
        int fragmentTop = (int) (sourceImageHeight * boundingBox.getTop().floatValue());
        int fragmentWidth = (int) (sourceImageWidth * boundingBox.getWidth());
        int fragmentHeight = (int) (sourceImageHeight * boundingBox.getHeight());

        return sourceImage.getSubimage(fragmentLeft, fragmentTop, fragmentWidth, fragmentHeight);
    }


}
