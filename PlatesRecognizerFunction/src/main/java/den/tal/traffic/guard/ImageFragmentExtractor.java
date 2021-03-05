package den.tal.traffic.guard;

import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.Geometry;
import com.amazonaws.services.rekognition.model.Point;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.utils.Pair;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static java.lang.Math.*;

@Slf4j()
public class ImageFragmentExtractor {

    private float scale = 1.0f;
    private final Color color = Color.YELLOW;

    public ImageFragmentExtractor(float scale) {
        this.scale = scale;
    }

    public BufferedImage extractFragment(BoundingBox boundingBox, BufferedImage sourceImage) {
        int sourceImageWidth = sourceImage.getWidth();
        int sourceImageHeight = sourceImage.getHeight();
        int fragmentLeft;
        int fragmentTop;
        int fragmentWidth;
        int fragmentHeight;
        if (scale == 1) {
            log.debug("Scaling is not needed.");
            fragmentLeft =Float.valueOf(sourceImageWidth * boundingBox.getLeft()).intValue();
            fragmentTop = Float.valueOf(sourceImageHeight * boundingBox.getTop()).intValue();
            fragmentWidth = Float.valueOf(sourceImageWidth * boundingBox.getWidth()).intValue();
            fragmentHeight = Float.valueOf(sourceImageHeight * boundingBox.getHeight()).intValue();
        } else {
            log.debug("Scale fragment to {}", scale);
            Pair<Integer, Integer> newXY = newCoordinates(boundingBox, sourceImageWidth, sourceImageHeight);
            Pair<Integer, Integer> newWidthHeight = newSizes(boundingBox, sourceImageWidth, sourceImageHeight);
            fragmentLeft = newXY.left();
            fragmentTop = newXY.right();
            fragmentWidth = newWidthHeight.left();
            if (fragmentLeft + fragmentWidth > sourceImageWidth) {
                log.warn("Fragment width is too big. Crop it.");
                fragmentWidth = fragmentWidth - (fragmentLeft + fragmentWidth - sourceImageWidth);
            }
            fragmentHeight = newWidthHeight.right();
            if (fragmentTop + fragmentHeight > sourceImageHeight) {
                log.warn("Fragment height is too big. Crop it.");
                fragmentHeight = fragmentHeight - (fragmentTop + fragmentHeight - sourceImageHeight);
            }
        }

        return sourceImage.getSubimage(fragmentLeft, fragmentTop, fragmentWidth, fragmentHeight);
    }

    public BufferedImage boundFragment(Geometry geometry, BufferedImage sourceImage, boolean fillPolygon) {
        List<Point> polygonPoints = geometry.getPolygon();
        int[] xPointCoordinates = new int[polygonPoints.size()];
        int[] yPointCoordinates = new int[xPointCoordinates.length];
        for (int i = 0; i < polygonPoints.size(); i++) {
            xPointCoordinates[i] = (int) (sourceImage.getWidth() * polygonPoints.get(i).getX());
            yPointCoordinates[i] = (int) (sourceImage.getHeight() * polygonPoints.get(i).getY());
        }
        Polygon polygon = new Polygon(xPointCoordinates, yPointCoordinates, polygonPoints.size());
        Graphics graphics = sourceImage.getGraphics();
        graphics.setColor(color);
        graphics.drawPolygon(polygon);
        if (fillPolygon) {
            graphics.fillPolygon(polygon);
        }

        return sourceImage;
    }


    /**
     * Pair<NewXCoordinate,NewYCoordinate>
     */
    private Pair<Integer, Integer> newCoordinates(BoundingBox boundingBox, int sourceImageWidth,
                                                          int sourceImageHeight)
    {
        log.debug("Scale frame: {}.", scale);
        int boundingBoxWidth = Float.valueOf(boundingBox.getWidth() * sourceImageWidth).intValue();
        int boundingBoxHeight = Float.valueOf(boundingBox.getHeight() * sourceImageHeight).intValue();
        Pair<Double, Double> movementXY = getMovementXY(boundingBoxWidth, boundingBoxHeight);
        Double movementOnX = movementXY.left();
        Double movementOnY = movementXY.right();
        Float newXFloat = Float.valueOf(boundingBox.getLeft() * sourceImageWidth - movementOnX.floatValue());
        if (newXFloat.intValue() < 0) {
            log.warn("Scale value {} is too big to fit into image on horizontal.", scale);
        }
        Integer newX = newXFloat.intValue() > 0 ? newXFloat.intValue() : 0;
        Float newYFloat = Float.valueOf(boundingBox.getTop() * sourceImageHeight - movementOnY.floatValue());
        if (newYFloat.intValue() < 0) {
            log.warn("Scale value {} is too big to fit into image on vertical.", scale);
        }
        Integer newY = newYFloat.intValue() > 0 ? newYFloat.intValue() : 0;

        return Pair.of(newX, newY);
    }

    /**
     * Return Pair<MovementOnX, MovementOnY>
     */
    private Pair<Double, Double> getMovementXY(int boundingBoxWidth, int boundBoxHeight) {
        double sqrt = sqrt(boundBoxHeight * boundBoxHeight + boundingBoxWidth * boundingBoxWidth);
        //Z is a hypotenuse of increment
        double Z = sqrt * scale - sqrt;
        //B is a horizontal cathetus
        Double B = sqrt((Z * Z * boundingBoxWidth * boundingBoxWidth) / (boundBoxHeight * boundBoxHeight
                + boundingBoxWidth * boundingBoxWidth));
        //A is a vertical cathetus
        Double A = (B * boundBoxHeight) / boundingBoxWidth;

        return Pair.of(B, A);
    }

    /**
     * Pair<NewWidth, NewHeight>
     */
    private Pair<Integer, Integer> newSizes(BoundingBox boundingBox, int sourceImageWidth, int sourceImageHeight) {
        int boundingBoxWidth = Float.valueOf(boundingBox.getWidth() * sourceImageWidth).intValue();
        int boundingBoxHeight = Float.valueOf(boundingBox.getHeight() * sourceImageHeight).intValue();
        Pair<Double, Double> movementXY = getMovementXY(boundingBoxWidth, boundingBoxHeight);
        Double movementOnX = movementXY.left();
        Double movementOnY = movementXY.right();
        Integer newWidth = Double.valueOf(movementOnX * 2 + boundingBoxWidth).intValue();
        Integer newHeight = Double.valueOf(movementOnY * 2 + boundingBoxHeight).intValue();

        return Pair.of(newWidth, newHeight);
    }
}
