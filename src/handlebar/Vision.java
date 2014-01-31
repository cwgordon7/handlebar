package handlebar;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import org.opencv.core.*;
import org.opencv.highgui.*;

public class Vision {
	static VideoCapture camera;
	public static volatile double greenBall = Double.NaN;
	public static volatile double redBall = Double.NaN;
	private static boolean debug = false;
	private static JLabel cameraPane;
	private static JLabel opencvPane;
	protected static Mat getSamplePicture() {
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return Highgui.imread("/home/cwgordon7/Pictures/image3.jpg", Highgui.CV_LOAD_IMAGE_COLOR);
		//return Highgui.imread("/home/cwgordon7/Pictures/image02.png", Highgui.CV_LOAD_IMAGE_COLOR);
	}

	protected static Mat getVideoPicture() {
		if (camera == null) {
			camera = new VideoCapture();
			camera.open(1);
		}
		Mat image = new Mat();
		// Wait until the camera has a new frame
		while (!camera.read(image)) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return image;
	}
	
	public static void setup() {
		// Load the OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		// Setup the camera
		camera = new VideoCapture();
		camera.open(1);

	}

	public static void main(String args[]) {
		setup();

		// Create GUI windows to display camera output and OpenCV output
		int width = (int) (camera.get(Highgui.CV_CAP_PROP_FRAME_WIDTH));
		int height = (int) (camera.get(Highgui.CV_CAP_PROP_FRAME_HEIGHT));
		cameraPane = createWindow("Camera output", width, height);
		opencvPane = createWindow("OpenCV output", width, height);

		// Main loop
		process();
	}
	
	public static void process() {
			Mat m = getVideoPicture();
			if (debug) {
				updateWindow(cameraPane, m);
			}
			Mat pm_green = ImageProcessor.process(m.clone(), false);
			Vision.greenBall = ImageProcessor.getBearing(pm_green);
			Mat pm_red = ImageProcessor.process(m.clone(), true);
			Vision.redBall = ImageProcessor.getBearing(pm_red);
			if (debug) {
				updateWindow(opencvPane, pm_green);
			}
			Thread.yield();
	}

    private static JLabel createWindow(String name, int width, int height) {    
        JFrame imageFrame = new JFrame(name);
        imageFrame.setSize(width, height);
        imageFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel imagePane = new JLabel();
        imagePane.setLayout(new BorderLayout());
        imageFrame.setContentPane(imagePane);

        imageFrame.setVisible(true);
        return imagePane;
    }

    private static void updateWindow(JLabel imagePane, Mat mat) {
    	int w = (int) (mat.size().width);
    	int h = (int) (mat.size().height);
    	if (imagePane.getWidth() != w || imagePane.getHeight() != h) {
    		//imagePane.setSize(w, h);
    	}
    	BufferedImage bufferedImage = Mat2Image.getImage(mat);
    	imagePane.setIcon(new ImageIcon(bufferedImage));
    }
}
