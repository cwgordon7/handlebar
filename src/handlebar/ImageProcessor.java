package handlebar;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;


public class ImageProcessor {
	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	// Input: an image from the camera
	// Output: the OpenCV-processed image

	// (In practice it's a little different:
	//  the output image will be for your visual reference,
	//  but you will mainly want to output a list of the locations of detected objects.)
	public static Mat process(Mat rawImage, boolean red) {
		//Mat processedImage = new Mat();
		Mat hsv = preprocess(rawImage);
		Mat bw = new Mat();
		Imgproc.cvtColor(hsv, bw, Imgproc.COLOR_HSV2BGR);
		Imgproc.cvtColor(bw, bw, Imgproc.COLOR_BGR2GRAY);
		List<Mat> hsv_layers = new ArrayList<Mat>();
		Core.split(hsv, hsv_layers);
		// Separate into h, s, and v layers.
		Mat h = hsv_layers.get(0);
		Mat s = hsv_layers.get(1);
		Mat v = hsv_layers.get(2);
		Mat tmp1 = new Mat();
		Mat tmp2 = new Mat();

		// Saturation mask.
		Mat s_mask = new Mat();
		Imgproc.threshold(s, s_mask, 50, 255, Imgproc.THRESH_BINARY);

		// Red hue mask.
		Mat h_mask_red = new Mat();
		Imgproc.threshold(h, tmp1, 12, 255, Imgproc.THRESH_BINARY_INV);
		Imgproc.threshold(h, tmp2, 168, 255, Imgproc.THRESH_BINARY);
		Core.bitwise_or(tmp1, tmp2, h_mask_red);

		// Green hue mask;
		Mat h_mask_green = new Mat();
		Imgproc.threshold(h, tmp1, 80, 255, Imgproc.THRESH_BINARY_INV);
		Imgproc.threshold(h, tmp2, 40, 255, Imgproc.THRESH_BINARY);
		Core.bitwise_and(tmp1, tmp2, h_mask_green);

		// AND the hue masks with the saturation mask.
		Core.bitwise_and(h_mask_red, s_mask, tmp1);
		Core.bitwise_and(h_mask_green, s_mask, tmp2);

		return red ? tmp1 : tmp2;
	}

	public static Mat preprocess(Mat img) {
		Mat processed = new Mat();
		// Gaussian blur to drastically reduce noise.
		Imgproc.GaussianBlur(img, processed, new Size(9, 9), 5);
		Imgproc.cvtColor(processed, processed, Imgproc.COLOR_BGR2HSV);
		return processed;
	}

	public static double getBearing(Mat binImg) {
		Rect bb = largestBlob(binImg.clone());
		if (bb == null) {
			return Double.NaN;
		}
		int centerPixelX = bb.x + bb.width / 2;
		final double CAMERA_ANGLE = 120;
		double deg = CAMERA_ANGLE * (double)centerPixelX / (double)binImg.width() - CAMERA_ANGLE / 2;
		return deg;
	}

	public static Rect largestBlob(Mat binaryImg) {
		double maxArea = 100.0;
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		MatOfPoint largestContour = null;
		Imgproc.findContours(binaryImg, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
		for (MatOfPoint contour : contours) {
		    double area = Imgproc.contourArea(contour);
		    if (area > maxArea) {
		        maxArea = area;
		        largestContour = contour;
		    }
		}
		if (largestContour != null) {
			return Imgproc.boundingRect(largestContour);
		}
		else {
			return null;
		}
	}
}