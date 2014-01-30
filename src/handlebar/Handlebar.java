package handlebar;

import handlebar.PathFinder.NoPathFoundException;
import comm.BotClientMap;
import comm.BotClientMap.Point;

public class Handlebar {
	public static void main(String[] args) {
		BotClientMap map = BotClientMap.getDefaultMap();
		Robot robot = new Robot(map.startPose.theta);
		robot.setSorter(robot.SORTER_GREEN);
		Navigator nav = new Navigator(robot, map);
		// Uncomment to draw the map, for reference.
		// map.drawMap();

		// Uncomment to test moving to a point.
//		try {
//			nav.moveToPoint(new Point(2.5, 3.5));
//		} catch (NoPathFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

		// Uncomment to debug the sonars.
//		while (true) {
//			System.out.println(robot.getSonar1() + " | " + robot.getSonar2() + " | " + robot.getSonar3());
//			try {
//				Thread.sleep(100);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			Thread.yield();
//		
	}
}
