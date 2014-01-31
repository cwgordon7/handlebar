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
	public volatile ProbabilisticPose probPose;
	private volatile Mode mode = Mode.NEUTRAL;
	private volatile double targetHeading; // Radians.
	private volatile double speed; // Between 0 and 1.
	private volatile double distance; // Inches.

	private final Robot robot;
	private final BotClientMap map;
	private long lastRecalcMillis = 0;

	private final double TURN_THRESHOLD_RADIANS = Math.PI / 18.0; // 10 degrees.
	public Navigator(Robot bot, BotClientMap m) {
		this.robot = bot;
		this.map = m;
		pose = m.startPose;
		targetHeading = pose.theta;
		probPose = new ProbabilisticPose(m.startPose, 1000);
        // TODO: Tune these parameters based on experimentation.
        new PidController(1, 0, 0.75,
        		new ErrorCalculator() {
		        	@Override
			        public double getError() {
		        		final double dTheta = robot.getHeadingRadians() - pose.theta;
		        		final double distanceSinceLastUpdate = (robot.getTotalDistance() - distance) / map.gridSize; // The "unit" here is the grid size, or 22 inches. EG if the robot has gone 44 inches, the "distance travelled" will be 2 grid squares.
		        		distance = robot.getTotalDistance();
						if (mode.equals(Mode.STRAIGHT)) {
							probPose.perturb(distanceSinceLastUpdate, dTheta);
						}
						else {
							probPose.perturb(0.0, dTheta);
						}
						if (lastRecalcMillis != 0 && System.currentTimeMillis() - lastRecalcMillis > 2000) {
							System.out.println("Resampling");
							probPose.resample(map, new double[] {  inchesToGridUnits(robot.getIRLeft(), map), inchesToGridUnits(robot.getIRFront(), map), inchesToGridUnits(robot.getIRRight(), map) });
							lastRecalcMillis = System.currentTimeMillis();
						}
						else if (lastRecalcMillis == 0) {
							lastRecalcMillis = System.currentTimeMillis();
						}
		        		pose = probPose.representativePose();
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
			try {
				Thread.sleep(30);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (until - this.distance < 5) {
				this.speed = Math.min(0.3, this.speed);
			}
			if (until - this.distance < 2) {
				this.speed = Math.min(0.2, this.speed);
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
			forwardSquares(0.3, Math.sqrt((point.y - pose.y) * (point.y - pose.y) + (point.x - pose.x) * (point.x - pose.x)));
		}
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

	public void depositGreenBalls() {
		// TODO Auto-generated method stub
		System.out.println("...");
	}

	public void depositRedBalls() {
		System.out.println("...");
	}

	public void spin() {
		for (int i = 0; i <= 8; i++) {
			System.out.println("turning... " + i);
			turnRadians(Math.PI / 4);
			if (interrupt()) {
				System.out.println("Interrupted.");
				interrupted();
				return;
			}
		}
	}

	private boolean interrupt() {
		return (!Double.isNaN(Vision.greenBall) && (robot.numRedBalls < robot.RED_BALL_CAPACITY)) ||
			   (!Double.isNaN(Vision.greenBall) && (robot.numGreenBalls < robot.GREEN_BALL_CAPACITY)); // TODO: or timeout?
	}
	private void interrupted() {
		if (!Double.isNaN(Vision.greenBall) && (robot.numGreenBalls < robot.GREEN_BALL_CAPACITY)) {
			this.getGreenBall();
		}
		else if (!Double.isNaN(Vision.greenBall) && (robot.numRedBalls < robot.RED_BALL_CAPACITY)) {
			this.getRedBall();
		}
		System.out.println("Just kidding?");
	}

	public void getGreenBall() {
		System.out.println("Tracking green ball.");
		while (true) {
			if (Double.isNaN(Vision.greenBall)) {
				break;
			}
			System.out.println(Vision.greenBall);
			targetHeading = pose.theta + (Vision.greenBall * Math.PI / 180.0);
			if (Math.abs(targetHeading - pose.theta) <= TURN_THRESHOLD_RADIANS) {
				forwardSquares(0.5, inchesToGridUnits(robot.getIRFront(), map));
			}
		}
		try {
			robot.setSorter(robot.SORTER_GREEN);
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Nope.");
		halt();
	}

	public void getRedBall() {
		System.out.println("Tracking red ball.");
		while (true) {
			if (Double.isNaN(Double.NaN)) {
				break;
			}
			targetHeading = pose.theta + (Vision.redBall * Math.PI / 180.0);
			if (Math.abs(targetHeading - pose.theta) <= TURN_THRESHOLD_RADIANS) {
				forwardSquares(0.5, inchesToGridUnits(robot.getIRFront(), map));
			}
		}
		try {
			robot.setSorter(robot.SORTER_RED);
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		halt();
	}
}
