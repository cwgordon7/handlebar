package handlebar;

import java.awt.geom.Line2D;
import java.util.List;

import handlebar.PathFinder.NoPathFoundException;
import comm.BotClientMap;
import comm.BotClientMap.Point;
import comm.BotClientMap.Pose;
import comm.BotClientMap.Wall;

public class Navigator {
	private enum Mode { TURN, STRAIGHT, WALL_FOLLOW, NEUTRAL, STOP }

	public volatile Pose pose;
	private volatile Mode mode = Mode.NEUTRAL;
	private volatile double targetHeading; // Radians.
	private volatile double speed; // Between 0 and 1.
	private volatile double distance; // Inches.

	private final Robot robot;
	private final BotClientMap map;

	private final double TURN_THRESHOLD_RADIANS = Math.PI / 36.0; // 5 degrees.
	public Navigator(Robot bot, BotClientMap m) {
		this.robot = bot;
		this.map = m;
		pose = m.startPose;
        // TODO: Tune these parameters based on experimentation.
        new PidController(1, 0, 0.75,
        		new ErrorCalculator() {
		        	@Override
			        public double getError() {
		        		double thetaAvg = (robot.getHeadingRadians() + pose.theta) / 2.0;
		        		pose.theta = robot.getHeadingRadians();
		        		double distanceSinceLastUpdate = (robot.getTotalDistance() - distance) / map.gridSize; // The "unit" here is the grid size, or 22 inches. EG if the robot has gone 44 inches, the "distance travelled" will be 2 grid squares.
		        		distance = robot.getTotalDistance();
		        		if (mode.equals(Mode.STRAIGHT)) {
			        		pose.x += Math.cos(thetaAvg) * distanceSinceLastUpdate;
			        		pose.y += Math.sin(thetaAvg) * distanceSinceLastUpdate;
		        		}
		        		switch (mode) {
			        		case WALL_FOLLOW:
			        			return 0.0; // TODO
			        		case NEUTRAL:
			        		case STOP:
			        			return 0.0;
			        		case TURN:
			        		case STRAIGHT:
			        		default:
			        			return normalize(targetHeading - pose.theta);
		        		}
			        }
			    },
		        new ErrorHandler() {
					@Override
					public void handleError(double error) {
						double m1 = error / Math.PI;
						double m2 = error / -Math.PI;
						switch (mode) {
							case TURN:
								robot.setMotors(m1, m2);
								break;
							case STRAIGHT:
								robot.setMotors(m1 + speed, m2 + speed);
								break;
							case WALL_FOLLOW:
								// TODO;
								break;
							case NEUTRAL:
								break;
							case STOP:
								robot.setMotors(0, 0);
								break;
						}
					}
		       });
	}

	/**
	 * Changes the mode of the navigator.
	 */
	private void setMode(Mode m) {
		this.mode = m;
	}

	/**
	 * Moves forwards.
	 */
	public void forwardInches(double speed, double inches) {
		this.speed = speed;
		setMode(Mode.STRAIGHT);
		double until = this.distance + inches;
		while (this.distance < until) {
			if (Math.random() < 0.01) {
				System.out.println(this.distance + " < " + until);
			}
			try {
				Thread.sleep(30);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Thread.yield();
		}
		setMode(Mode.TURN);
	}

	/**
	 * Moves forwards.
	 */
	public void forwardSquares(double speed, double squares) {
		forwardInches(speed, squares * map.gridSize);
	}

	/**
	 * Turns to a relative heading.
	 */
	public void turnRadians(double radians) {
		turnToHeadingRadians(this.targetHeading + radians);
	}

	/**
	 * Turns to an absolute heading.
	 */
	public void turnToHeadingRadians(double radians) {
		this.targetHeading = radians;
		setMode(Mode.TURN);
		while (Math.abs(this.pose.theta - targetHeading) > TURN_THRESHOLD_RADIANS) {
			try {
				Thread.sleep(30);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Thread.yield();
		}
	}

	/**
	 * Halt.
	 */
	public void halt() {
		setMode(Mode.STOP);
	}

	/**
	 * Moves to a grid point.
	 */
	public void moveToPoint(Point p) throws NoPathFoundException {
		List<Point> points = PathFinder.findPath(map, pose, p);
		boolean first = true;
		for (Point point : points) {
			if (first) {
				first = false;
				continue;
			}
			System.out.println("At " + pose + ", moving to " + point);
			double theta = Math.atan2(point.y - pose.y, point.x - pose.x);
			System.out.println("Turning to " + (int)180 / Math.PI * theta);
			turnToHeadingRadians(theta);
			System.out.println("Forward " + Math.sqrt((point.y - pose.y) * (point.y - pose.y) + (point.x - pose.x) * (point.x - pose.x)));
			forwardSquares(0.5, Math.sqrt((point.y - pose.y) * (point.y - pose.y) + (point.x - pose.x) * (point.x - pose.x)));
		}
	}

	/**
	 * Probabilistic re-localization based on gaussian error assumption around estimated location and data from ultrasonic sensors.
	 */
	// IDEA: State should be a probability distribution rather than a concrete vector.
	public void relocalize() {
		// Save the original pose.
		Pose originalPose = pose;
		// TOOO
		//double[] sonarEstimates = mapBasedSonarEstimates();
		final double NUM_GUESSES = 100;
		final double PROB_THRESH = 0.01;
		for (int i = 0; i < NUM_GUESSES; i++) {
			// Perturb according to some gaussian, score; calculate probability
			// TODO finish
		}
		System.out.println(sonarEstimates[0] + " | " + sonarEstimates[1] + " | " + sonarEstimates[2]);
	}

	/**
	 * Utility function that converts from meters to grid units.
	 */
	public static double metersToGridUnits(double meters, BotClientMap map) {
		final double INCHES_PER_METER = 39.3701;
		return inchesToGridUnits(meters * INCHES_PER_METER, map);
	}

	/**
	 * Utility function that converts from inches to grid units.
	 */
	public static double inchesToGridUnits(double inches, BotClientMap map) {
		return inches / map.gridSize;
	}

	/**
	 * Utility function that normalizes an angle in radians to a value between
	 * negative pi and pi.
	 * @param radians
	 */
	public static double normalize(double radians) {
		while (radians > Math.PI) {
			radians -= 2 * Math.PI;
		}
		while (radians < -Math.PI) {
			radians += 2 * Math.PI;
		}
		return radians;
	}
}
