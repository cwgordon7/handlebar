package handlebar;

import handlebar.PathFinder.NoPathFoundException;
import comm.BotClientMap;
import comm.BotClientMap.Point;
import comm.BotClientMap.Pose;

public class Navigator {
	public enum Mode { TURN, STRAIGHT, WALL_FOLLOW, NEUTRAL }

	private volatile Pose pose;
	private volatile Mode mode;
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
		        		pose.x += Math.cos(thetaAvg) * distanceSinceLastUpdate;
		        		pose.y += Math.sin(thetaAvg) * distanceSinceLastUpdate;
		        		switch (mode) {
			        		case WALL_FOLLOW:
			        			return 0.0; // TODO
			        		case NEUTRAL:
			        			return 0.0;
			        		case TURN:
			        		case STRAIGHT:
			        		default:
			        			return normalize(pose.theta - targetHeading);
		        		}
			        }
			    },
		        new ErrorHandler() {
					@Override
					public void handleError(double error) {
						double m1 = error / -Math.PI;
						double m2 = error / Math.PI;
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
			Thread.yield();
		}
		setMode(Mode.NEUTRAL);
	}

	/**
	 * Moves forwards.
	 */
	public void forwardSquares(double speed, double squares) {
		forwardInches(speed, squares * map.gridSize);
	}

	/**
	 * Turns.
	 */
	public void turnRadians(double radians) {
		this.targetHeading += radians;
		setMode(Mode.TURN);
		while (Math.abs(this.pose.theta - targetHeading) > TURN_THRESHOLD_RADIANS) {
			Thread.yield();
		}
		setMode(Mode.NEUTRAL);
	}

	/**
	 * Moves to a grid point.
	 */
	public void moveToPoint(Point p) {
		try {
			PathFinder.findPath(map, pose, p);
		} catch (NoPathFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Utility function that normalizes an angle in radians to a value between
	 * negative pi and pi.
	 * @param radians
	 */
	private double normalize(double radians) {
		while (radians > Math.PI) {
			radians -= 2 * Math.PI;
		}
		while (radians < -Math.PI) {
			radians += 2 * Math.PI;
		}
		return radians;
	}
}
