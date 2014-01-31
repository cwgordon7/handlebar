package handlebar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JFrame;

import comm.BotClientMap;
import comm.BotClientMap.Point;
import comm.BotClientMap.Wall;

/**
 * OTHER IDEAS: Same algorithm but with wall distances? Same algorithm but just as incremental PID targets?
 * @author cwgordon7
 *
 */
public class PathFinder {
	public static List<Point> findPath(BotClientMap m, Point origin, Point destination) throws NoPathFoundException {
		PathFinderPoint start = new PathFinderPoint(new Point(origin.x, origin.y), distance(origin, destination));
		PathFinderPoint goal = new PathFinderPoint(destination, 0);
		Set<PathFinderPoint> closedset = new HashSet<PathFinderPoint>();
		Queue<PathFinderPoint> openset = new PriorityQueue<PathFinderPoint>();
		openset.add(start);
		Map<PathFinderPoint, PathFinderPoint> came_from = new HashMap<PathFinderPoint, PathFinderPoint>();

		Map<PathFinderPoint, Double> g_score = new HashMap<PathFinderPoint, Double>();
		g_score.put(start, 0.0);
		while (!openset.isEmpty()) {
			PathFinderPoint point = openset.poll();
			if (distance(point.p, goal.p) < 0.25 * Math.sqrt(2)) {
				return reconstructPath(came_from, goal);
			}
			closedset.add(point);
			for (Point neighbor : point.neighbors()) {
				if (closedset.contains(new PathFinderPoint(neighbor, 0))) {
					continue;
				}
				boolean invalid = false;
				for (Wall w : m.walls) {
					double dist = Line2D.ptSegDist(w.start.x, w.start.y, w.end.x, w.end.y, neighbor.x, neighbor.y);
					if (dist < 0.25) {
						invalid = true;
					}
				}
				if (invalid) {
					continue;
				}
				double tentative_g_score = g_score.get(point) + distance(point.p, neighbor);
				if (!openset.contains(new PathFinderPoint(neighbor, 0)) || tentative_g_score < g_score.get(new PathFinderPoint(neighbor, 0))) {
					double calculated_f_score = tentative_g_score + distance(neighbor, destination);
					PathFinderPoint neighborPoint = new PathFinderPoint(neighbor, calculated_f_score);
					came_from.put(neighborPoint, point);
					g_score.put(neighborPoint, tentative_g_score);
					if (!openset.contains(neighbor)) {
						openset.add(neighborPoint);
					}
				}
			}
		}
		throw new NoPathFoundException();
	}

	public static List<Point> reconstructPath(Map<PathFinderPoint, PathFinderPoint> came_from, PathFinderPoint current_node) {
		List<Point> list;
		if (came_from.containsKey(current_node)) {
			list = reconstructPath(came_from, came_from.get(current_node));
		}
		else {
			list = new ArrayList<Point>();
		}
		list.add(current_node.p);
		return list;
	}

	public static class PathFinderPoint implements Comparable<PathFinderPoint> {
		private Point p;
		private double cost;
		public PathFinderPoint(Point p, double cost) {
			this.p = p;
			this.cost = cost;
		}

		@Override
		public int compareTo(PathFinderPoint other) {
			// TODO Auto-generated method stub
			if (this.cost > other.cost) {
				return 1;
			}
			else if (this.cost < other.cost) {
				return -1;
			}
			else {
				return 0;
			}
		}

		public List<Point> neighbors() {
			List<Point> list = new ArrayList<Point>();
			for (int x = -1; x <= 1; x++) {
				for (int y = -1; y <= 1; y++) {
					if (x == 0 && y == 0) {
						continue;
					}
					list.add(new Point(p.x + 0.5 * x, p.y + 0.5 * y));
				}
			}
			return list;
		}

		@Override
		public boolean equals(Object obj) {
			return this.toString().equals(obj.toString());
		}

		@Override
		public int hashCode() {
			return p.toString().hashCode();
		}

		@Override
		public String toString() {
			return p.toString();
		}
	}

	public static class NoPathFoundException extends Exception {
		private static final long serialVersionUID = -3290304415089247996L;
	}

	private static double distance(Point a, Point b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
	}

	public static void drawMap() {
		JFrame jf = new JFrame();
		jf.setContentPane(new WallPainter());
		jf.setSize(500,500);
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setBackground(Color.white);
		jf.setVisible(true);
	}
	
	private static class WallPainter extends JComponent {
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
			
			g.fillOval((int) (size*m.startPose.x) - (int)(size/4.0),(int) (-size*m.startPose.y) - (int)(size/4.0), (int)(size/2.0), (int)(size/2.0));
			
			double DX = Math.cos(m.startPose.theta) * size/2.0;
			double DY = -Math.sin(m.startPose.theta) * size/2.0;
			g.drawLine((int) (size*m.startPose.x), (int) (-size*m.startPose.y), (int) (size*m.startPose.x + DX), (int) (-size*m.startPose.y + DY));

			// Draw path.
			g.setColor(Color.CYAN);
			g.fillOval((int) (size*target.x) - (int)(size/4.0),(int) (-size*target.y) - (int)(size/4.0), (int)(size/2.0), (int)(size/2.0));
			g.setColor(Color.GRAY);
			Point last = null;
			for (Point dot : dots) {
				g.fillOval((int) (size*dot.x) - (int)(size/8.0),(int) (-size*dot.y) - (int)(size/8.0), (int)(size/4.0), (int)(size/4.0));
				if (last != null) {
					g.drawLine((int)(dot.x * size), (int)(dot.y*-size), (int)(last.x * size), (int)(last.y*-size));
				}
				last = dot;
			}
  			
		}
	}

	private static BotClientMap m;
	private static List<Point> dots;
	private static Point target = new Point(BotClientMap.getDefaultMap().startPose.x, BotClientMap.getDefaultMap().startPose.y);
	public static void main(String[] args) {
		m = BotClientMap.getDefaultMap();
		try {
			dots = findPath(m, new Point(1.0, 3.5), target);
		} catch (NoPathFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		drawMap();
	}
}
