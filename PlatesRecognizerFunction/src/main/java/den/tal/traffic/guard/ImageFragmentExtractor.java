package den.tal.traffic.guard;

import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.Geometry;
import com.amazonaws.services.rekognition.model.Point;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class ImageFragmentExtractor {

    private int scale = 1;
    private final Color color = Color.YELLOW;

    public ImageFragmentExtractor(int scale) {
        this.scale = scale;
    }

    public BufferedImage extractFragment(BoundingBox boundingBox, BufferedImage sourceImage) {
        int sourceImageWidth = sourceImage.getWidth();
        int sourceImageHeight = sourceImage.getHeight();
        int fragmentLeft = (int) (sourceImageWidth * boundingBox.getLeft());
        int fragmentTop = (int) (sourceImageHeight * boundingBox.getTop());
        int fragmentWidth = (int) (sourceImageWidth * boundingBox.getWidth());
        int fragmentHeight = (int) (sourceImageHeight * boundingBox.getHeight());

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
}
