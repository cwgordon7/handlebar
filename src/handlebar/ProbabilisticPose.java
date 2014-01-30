package handlebar;

import handlebar.PathFinder.NoPathFoundException;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JFrame;

import comm.BotClientMap;
import comm.BotClientMap.Point;
import comm.BotClientMap.Pose;
import comm.BotClientMap.Wall;

public class ProbabilisticPose {
	private final int NUM_TRIALS;
	private final static double MOTION_ERROR = 0.04; // 0.04 means on a 5 foot run, std error is 5 inches. (tbd experimentally)
	private final static double ULTRASOUND_FLAKINESS = 0.05; // 5% of ultrasound readings are crap.
	private final static double ANGLE_ERROR = 0.02; // Angle error (tbd experimentally) 
	private final static double GYRO_DRIFT = 0.0008; // Look this up in the gyro specs; I think it was on the order of 3 degrees / minute?
	private final static double SONAR_STDERR = 0.1;
	private Pose[] poses;
	private double[] probs;
	private double scale = 1;
	private final static Random random = new Random();

	public ProbabilisticPose(Pose initial, int numTrials) {
		this.NUM_TRIALS = numTrials;
		this.poses = new Pose[NUM_TRIALS];
		this.probs = new double[NUM_TRIALS];
		// Set the first pose initially. "probs" double is cumulative. So initially will look like [1, 1, 1, 1, 1, ...].
		// This lets us do binary search to find value. Eventually will look like [0.011, 0.021, 0.029, 0.043... scale].
		this.poses[0] = initial;
		for (int i = 0; i < NUM_TRIALS; i++) {
			this.probs[i] = 1;
		}
	}

	private Pose find(double k, int low, int high) {
		// TODO: Use heuristic to speed this up. 0.37 will be (on avg) 37% of the way through.
		if (low == high) {
			return poses[low];
		}
		int guess = (high + low) / 2;
		if (probs[guess] <= k) {
			return find(k, guess, high);
		}
		else {
			if (guess == 0 || probs[guess - 1] <= k) {
				return poses[guess];
			}
			return find(k, low, guess);
		}
	}

	/**
	 * SONAR ESTIMATES MUST BE CONVERTED TO GRID SIZED UNITS!
	 * @param dist
	 * @param angle
	 * @param map
	 * @param sonarReadings
	 */
	public void perturb(double dist, double angle) {
		for (int i = 0; i < NUM_TRIALS; i++) {
			if (poses[i] == null) {
				continue;
			}
			Pose p = poses[i];
			Pose newP = new Pose(p.x, p.y, p.theta);
			newP.theta += GYRO_DRIFT * random.nextGaussian() /* TODO: * dT? */ + angle * (1 + (ANGLE_ERROR * random.nextGaussian()));
			double distErr = dist * (1 + (MOTION_ERROR * random.nextGaussian()));
			newP.x += distErr * Math.cos(newP.theta);
			newP.y += distErr * Math.sin(newP.theta);
			poses[i] = newP;
		}
	}

	/**
	 * Resample probabilistically. This should be done periodically. Running this
	 * a second time without modifying the parameters or perturbing the pose will
	 * return the same thing, on average. 
	 */
	public void resample(BotClientMap map, double[] sonarReadings) {
		double j = 0.0;
		Pose[] newPoses = new Pose[NUM_TRIALS];
		double[] newProbs = new double[NUM_TRIALS];
		for (int i = 0; i < NUM_TRIALS; i++) {
			double k = random.nextDouble() * scale;
			Pose p = find(k, 0, NUM_TRIALS);
			Pose newP = new Pose(p.x, p.y, p.theta);
			newPoses[i] = newP;
			// DON'T multiply by previous probability. That factor is already represented by the random sampling.
			j += score(mapBasedSonarEstimates(newP, map), sonarReadings);
			newProbs[i] = j;
		}
		poses = newPoses;
		probs = newProbs;
		scale = j;
	}

	/**
	 * Sonar estimates in std grid-size units.
	 */
	private static double[] mapBasedSonarEstimates(Pose pose, BotClientMap map) {
		double thetaLeft = pose.theta + Math.PI / 2;
		double thetaStraight = pose.theta;
		double thetaRight = pose.theta - Math.PI / 2;
		final double TEN_METERS = Navigator.metersToGridUnits(10, map);
		double[] ss = new double[3];
		int i = 0;
		for (double theta : new double[] {thetaLeft, thetaStraight, thetaRight}) {
			double s = Double.POSITIVE_INFINITY;
			for (Wall w : map.walls) {
				if (Line2D.linesIntersect(w.start.x, w.start.y, w.end.x, w.end.y, pose.x, pose.y, pose.x + TEN_METERS * Math.cos(theta), pose.y + TEN_METERS * Math.sin(theta))) {
					double r;
					if (w.end.x - w.start.x != 0) {
						double m = (w.end.y - w.start.y) / (w.end.x - w.start.x);
						double b = w.start.y - (m * w.start.x);
						r = (m * pose.x + b - pose.y) / (Math.sin(theta) - m * Math.cos(theta));
					}
					else {
						double k = w.end.x;
						r = (k - pose.x) / Math.cos(theta);
					}
					if (r > 0 && r < s) {
						s = r;
					}
				}
			}
			ss[i] = s;
			i++;
		}
		return ss;
	}

	/**
	 * Scores a set of readings.
	 * @param sonarEstimates
	 * @param sonarReadings
	 * @return
	 */
	private double score(double[] sonarEstimates, double[] sonarReadings) {
		double p = 1.0 / Math.sqrt(scale); /* Keep the scale semi-normal here: If scale gets to small, this will make it bigger; if scale gets to large, this will make it smaller. This would not be necessary if we were doing proper Bayesian logic here. */
		for (int i = 0; i < sonarEstimates.length; i++) {
			double errorZ = (sonarReadings[i] - sonarEstimates[i]) / (Math.sqrt(2) * SONAR_STDERR);
			/** This is a bad approximation. TODO: Do better. */
			p *= ((ULTRASOUND_FLAKINESS) + (1 - ULTRASOUND_FLAKINESS) * normal(errorZ));
		}
		return p;
	}

	public void drawState(BotClientMap m) {
		JFrame jf = new JFrame();
		jf.setContentPane(new WallPainter(m));
		jf.setSize(500,500);
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setBackground(Color.white);
		jf.setVisible(true);
	}
	
	private class WallPainter extends JComponent {
		private BotClientMap m;
		private WallPainter(BotClientMap m) {
			this.m = m;
		}

		@Override
		public void paint(Graphics g) {
			super.paint(g);
			int size = 50;
			g.translate(1*size, 8*size);
		    ((Graphics2D)g).setStroke(new BasicStroke(3));

			Color[] colors = new Color[] {Color.black, Color.yellow, new Color(255,0,255), Color.green};
			for (Wall w : m.walls) {
				g.setColor(colors[w.type.ordinal()]);
				g.drawLine(size * (int)w.start.x, size * -(int)w.start.y, size * (int)w.end.x, size * -(int)w.end.y);
			}

			for (int i = 0; i < poses.length; i++) {
				double prob;
				if (i == 0) {
					prob = probs[i];
				}
				else {
					prob = probs[i] - probs[i-1];
				}
				int w =  (int)(255.0 * Math.min(1, NUM_TRIALS * prob / (1.5 * scale)));
				g.setColor(new Color(255, 255 - w, 255 - w));
				g.fillOval((int) (size * poses[i].x), (int)(-size * poses[i].y), 1, 1);
			}
		}
	}

	public static void main(String[] args) {
		BotClientMap m = BotClientMap.getDefaultMap();
		m.startPose = new Pose(5.0, 3.5, 0.0);
		ProbabilisticPose p = new ProbabilisticPose(m.startPose, 20000);
		try {
			p = moveToPointTest(p, m, new Pose(m.startPose.x, m.startPose.y, m.startPose.theta), new Point(2.5, 3.5));
		} catch (NoPathFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Moves to a grid point.
	 * INTERNAL; for TESTING ONLY.
	 * Returns "actual" pose.
	 */
	private static ProbabilisticPose moveToPointTest(ProbabilisticPose probPose, BotClientMap m, Pose pose, Point p) throws NoPathFoundException {
		List<Point> points = PathFinder.findPath(m, pose, p);
		boolean first = true;
		for (Point point : points) {
			if (first) {
				first = false;
				continue;
			}
			System.out.println("At " + pose + ", moving to " + point);
			double theta = Math.atan2(point.y - pose.y, point.x - pose.x);
			double dtheta = Navigator.normalize(theta - pose.theta);
			pose.theta = pose.theta + dtheta * (1 + (random.nextGaussian() * ANGLE_ERROR)) + random.nextGaussian() * GYRO_DRIFT;
			double dist = Math.sqrt((point.y - pose.y) * (point.y - pose.y) + (point.x - pose.x) * (point.x - pose.x));
			double distWithErr = dist * (1 + (MOTION_ERROR * random.nextGaussian()));
			pose.x += Math.cos(pose.theta) * distWithErr;
			pose.y += Math.sin(pose.theta) * distWithErr;
			double[] sonarReadings = mapBasedSonarEstimates(pose, m);
			// sonarReadings[0] = 10 * Math.random();
			// sonarReadings[1] = 9.9;
			//sonarReadings[2] = 9.9;
			for (int i = 0; i < 3; i++) { sonarReadings[i] *= (1.0 + random.nextGaussian() * SONAR_STDERR); }
			probPose.perturb(dist, dtheta);
			probPose.resample(m, sonarReadings);
			probPose.drawState(m);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return probPose;
	}

	/**
	 * Calculates a "representative" single pose of the distribution.
	 * @return
	 */
	public Pose representativePose() {
		// Weighted average of all poses.
		// TODO: this may fail horribly if there are multiple clusters. Hopefully that doesn't happen / data converges?
		// To do angle averages, we sum the weighted direction vectors and calculate the angle of the resulting sum vector.
		double x_avg = 0.0;
		double y_avg = 0.0;
		double thetaX = 0.0;
		double thetaY = 0.0;
		for (int i = 0; i < NUM_TRIALS; i++) {
			Pose p = poses[i];
			if (p == null) {
				continue;
			}
			x_avg += p.x * probs[i];
			y_avg += p.y * probs[i];
			thetaX += Math.cos(p.theta) * probs[i];
			thetaY += Math.sin(p.theta) * probs[i];
		}
		x_avg /= scale;
		y_avg /= scale;
		double theta = Math.atan2(thetaY, thetaX);
		return new Pose(x_avg, y_avg, theta);
	}

	/**
	 * Utility function: normal distribution.
	 */
	private static double normal(double z) {
		return Math.exp(-(z * z) / 2);
	}
}
