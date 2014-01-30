package handlebar;

import comm.BotClientMap;

public class Handlebar {
	public static void main(String args) {
		Robot robot = new Robot();
		BotClientMap map = BotClientMap.getDefaultMap();
		Navigator nav = new Navigator(robot, map);
		nav.forwardSquares(1, 1);
	}
}
