package handlebar;

import comm.MapleComm;
import comm.MapleIO.SerialPortType;
import devices.actuators.Cytron;
import devices.actuators.Servo1800A;
import devices.sensors.Encoder;
import devices.sensors.Gyroscope;
import devices.sensors.Ultrasonic;

public class Robot {
	private MapleComm maple;
	// Motor cytrons
	final private Cytron motorACytron = new Cytron(2, 1);
	final private Cytron motorBCytron = new Cytron(7, 6);
	// Servos. TODO: Other servos.
	final private Servo1800A sorterServo = new Servo1800A(3); 
	// Gyro
	final private Gyroscope gyro = new Gyroscope(1, 9);
	// Encoder
	final private Encoder encoder = new Encoder(33, 34); // TODO: Which is which? Wrong order?
	// Ultrasonic
	final private Ultrasonic sonic1 = new Ultrasonic(24, 23);
	final private Ultrasonic sonic2 = new Ultrasonic(26, 25);
	final private Ultrasonic sonic3 = new Ultrasonic(28, 27);

	// Internal tracking.
	private long lastTimeMillis;
	private double heading;

	// Robot dimensional constants.
	final public double WHEEL_RADIUS_INCHES = 3.875 / 2;
	final public int GREEN_BALL_CAPACITY = 10;
	final public int RED_BALL_CAPACITY = 4;
    // Speed scaling from 0.05 to 0.3. TODO: Raise the limit to go faster.
	final public double MIN_POWER = 0.05;
	final public double POWER_SCALE = 0.25;

	public Robot() {
		final Robot self = this;
		try {
			maple = new MapleComm(SerialPortType.LINUX);
			maple.registerDevice(motorACytron);
			maple.registerDevice(motorBCytron);
			maple.registerDevice(sorterServo);
			maple.registerDevice(gyro);
			maple.registerDevice(encoder);
			maple.registerDevice(sonic1);
			maple.registerDevice(sonic2);
			maple.registerDevice(sonic3);
			maple.initialize();

			// Continually update the sensor data.
			new Thread(new Runnable() {
				public void run() {
					while (true) {
						maple.updateSensorData();
						long time = System.currentTimeMillis();
						long millis = time - lastTimeMillis;
						heading += (millis * gyro.getAngularSpeed()) / 1000.0;
						lastTimeMillis = time;
						Thread.yield();
					}
				}
			}).start();
		}
        catch (Exception ex) {
            System.out.println(ex);
        }
	}

	/**
	 * Returns the Robot's current heading, in radians.
	 * @return
	 */
	public double getHeadingRadians() {
		return heading;
	}

	/**
	 * Gets the distance traveled, total, in inches.
	 */
	public double getTotalDistance() {
		return encoder.getTotalAngularDistance() * WHEEL_RADIUS_INCHES; // s = r theta? probably
	}

	/**
	 * Gets the distance given by sonar 1, in meters.
	 */
	public double getSonar1() {
		return sonic1.getDistance();
	}

	/**
	 * Gets the distance given by sonar 2, in meters.
	 */
	public double getSonar2() {
		return sonic1.getDistance();
	}

	/**
	 * Gets the distance given by sonar 3, in meters.
	 */
	public double getSonar3() {
		return sonic1.getDistance();
	}

	/**
	 * Sets the motor speed.
	 */
	public void setMotors( double powerA, double powerB ) {
		// Note: motorA is left motor, motorB is right Motor.
		powerA = Math.min(Math.max(-1, powerA), 1);
		powerB = Math.min(Math.max(-1, powerB), 1);
		powerA = MIN_POWER * Math.signum(powerA) + POWER_SCALE * powerA;
		powerB = MIN_POWER * Math.signum(powerB) + POWER_SCALE * powerB;
		// TODO: Is this actually helpful?  Even though this makes it look better, it might stop the system from converging.
		if (Math.abs(powerA) - MIN_POWER < 0.01) {
			powerA = 0;
		}
		if (Math.abs(powerB) - MIN_POWER < 0.01) {
			powerB = 0;
		}
		// TODO: Some wires got reversed somewhere down the line. We need
		// these negative signs now.
		motorACytron.setSpeed(-powerA);
		motorBCytron.setSpeed(-powerB);
		modified();
	}

	/**
	 * Sets the sorter.
	 */
	public void setSorter(double angleDegrees) {
		sorterServo.setAngle(angleDegrees);
		modified();
	}

	private void modified() {
		maple.transmit();
	}
}
