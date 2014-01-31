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
		// comment this out -v
		BotClientMap map = BotClientMap.getDefaultMap();
		Robot robot = new Robot(map.startPose.theta);
		robot.setSorter(robot.SORTER_GREEN);
		Navigator nav = new Navigator(robot, map);

		boolean spun = false;
		while (true) {
			if (robot.numGreenBalls == robot.GREEN_BALL_CAPACITY) {
				System.out.println("At green ball capacity - depositing green balls.");
				nav.depositGreenBalls();
				spun = false;
			}
			else if (robot.numRedBalls == robot.RED_BALL_CAPACITY) {
				System.out.println("At red ball capacity - depositing red balls.");
				nav.depositRedBalls();
				spun = false;
			}
			else if (robot.numGreenBalls > 0) {
				System.out.println("Depositing green balls.");
				nav.depositGreenBalls();
				spun = false;
			}
			else if (robot.numRedBalls > 0) {
				System.out.println("Depositing red balls.");
				nav.depositRedBalls();
				spun = false;
			}
			else if (!spun) {
				System.out.println("Spinning!");
				nav.spin();
				spun = true;
			}
			else {
				System.out.println("Random walk.");
				boolean moved = false;
				while (!moved) {
					try {
						Point p = map.randomPoint();
						nav.moveToPoint(p);
						moved = true;
					} catch (NoPathFoundException e) {
						// Unreachable point! Choose a different one.
					}
				}
				spun = false;
			}
		}
	}
}
