package devices.sensors;

public class Infrared extends AnalogInput {
	
	/*
	 * Takes one analog-in pin (labeled on the Maple as "AIN")
	 */
	public Infrared(int pin) {
		super(pin);
	}
	
	public double getDistance() {
		// 6 inches = 2900
		// 8 inches = 2680
		// 10 inches = 2350
		// 12 inches = 1850
		// 15 inches = 1300
		// 20 inches = 1150
		if (value == 0) {
			return Double.POSITIVE_INFINITY;
		}
		if (value > 2680) {
			return 6 + 2 * (2900.0 - (double)value)/ (2900.0 - 2680.0);
		}
		else if (value > 2350) {
			return 8 + 2 * (2680.0 - (double)value) / (2680.0 - 2350.0);
		}
		else if (value > 1850) {
			return 10 + 2 * (1850.0 - (double)value) / (2350.0 - 1850.0);
		}
		else {
			return 1 / ((double)value / 21000.0 - 0.00476190);
		}
	}
}
