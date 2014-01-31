package handlebar;

import BotClient.BotClient;
import handlebar.PathFinder.NoPathFoundException;
import comm.BotClientMap;
import comm.BotClientMap.Point;

public class Handlebar {
	public final static String BOT_CLIENT_SERVER = "18.150.7.174:6667";
	public final static String TEAM_16_TOKEN = "Nj5fd3q7pe";
	public static void main(String[] args) {
		/*BotClient bc = new BotClient(BOT_CLIENT_SERVER, TEAM_16_TOKEN, false);
		bc.send("status", "Status", "Waiting for game to start.");
		/*while (!bc.gameStarted()) {
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		BotClientMap map = new BotClientMap();
		map.load(bc.getMap());
		map.drawMap();*/
		BotClientMap map = BotClientMap.getDefaultMap();
		Robot robot = new Robot(map.startPose.theta);
		robot.setSorter(robot.SORTER_GREEN);
		Navigator nav = new Navigator(robot, map);
		try {
			nav.moveToPoint(new Point(0.5, 3.5));
		} catch (NoPathFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		nav.probPose.drawState(map);

		// Uncomment to draw the map, for reference.
		// map.drawMap();
		// nav.forwardSquares(0.5, 1);
		// nav.turnRadians(0);
		//nav.halt();
		// System.out.println("SUCCESS");

		// Uncomment to test moving to a point.
//		try {
//			nav.moveToPoint(new Point(2.5, 3.5));
//		} catch (NoPathFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

		// Uncomment to debug the sonars.
		/*while (true) {
			System.out.println(robot.getIRLeft() + " | " + robot.getIRFront() + " | " + robot.getIRRight());
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Thread.yield();
		}*/
	}
}
