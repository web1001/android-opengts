package org.gc.gts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.overlay.OpenStreetMapViewOverlayItem;

import android.util.Log;

public class LatitudeOverlayItem extends OpenStreetMapViewOverlayItem implements
		GtsConstants {

	private int precision;

	private LatitudeOverlayItem(String aTitle, String aDescription,
			GeoPoint aGeoPoint) {
		super(aTitle, aDescription, aGeoPoint);
	}

	public static LatitudeOverlayItem getInstance(OpenStreetMapView osmv,
			String user) {
		LatitudeOverlayItem latitudeItem = null;

		try {
			URL url = new URL(
					"http://www.google.com/latitude/apps/badge/api?user="
							+ user + "&type=atom");

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					url.openStream()));

			String line;

			double lon = 0, lat = 0;
			String title = null, radius = null;

			while ((line = reader.readLine()) != null) {
				if (line.trim().startsWith("<georss:point>")) {
					line = line.substring(line.indexOf(">") + 1);

					lat = Double.parseDouble(line.substring(0, line
							.indexOf(' ')));

					line = line.substring(line.indexOf(' ') + 1);

					lon = Double.parseDouble(line.substring(0, line
							.indexOf("</")));
				}
				if (line.trim().startsWith("<georss:radius>")) {
					radius = line.substring(line.indexOf(">") + 1, line
							.indexOf("</"));
				}
				if (line.trim().startsWith("<georss:featurename>")) {
					title = line.substring(line.indexOf(">") + 1, line
							.indexOf("</"));
				}
			}

			if (null != title) {
				latitudeItem = new LatitudeOverlayItem(title, "precision "
						+ radius + " meters", new GeoPoint(lat, lon));
				latitudeItem.setPrecision(Integer.parseInt(radius));
			}

			reader.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (null != latitudeItem)
			latitudeItem.startThread(osmv, user);

		return latitudeItem;
	}

	public void startThread(final OpenStreetMapView osmv, final String user) {
		Thread thread = new Thread(new Runnable() {

			public void run() {
				while (true) {
					try {
						Thread.sleep(5 * MINUTE);

						Log.i("latitude", "awake");

						URL url = new URL(
								"http://www.google.com/latitude/apps/badge/api?user="
										+ user + "&type=atom");

						BufferedReader reader = new BufferedReader(
								new InputStreamReader(url.openStream()));

						String line, radius;

						double lon = 0, lat = 0;

						while ((line = reader.readLine()) != null) {
							if (line.trim().startsWith("<georss:point>")) {
								line = line.substring(line.indexOf(">") + 1);

								lat = Double.parseDouble(line.substring(0, line
										.indexOf(' ')));

								line = line.substring(line.indexOf(' ') + 1);

								lon = Double.parseDouble(line.substring(0, line
										.indexOf("</")));
							}
							if (line.trim().startsWith("<georss:radius>")) {
								radius = line.substring(line.indexOf(">") + 1,
										line.indexOf("</"));
								precision = Integer.parseInt(radius);
							}
						}

						Log.i("latitude", "lat=" + lat + ", lon=" + lon);

						mGeoPoint.setCoordsE6((int) (lat * 1E6),
								(int) (lon * 1E6));

						reader.close();

						// osmv.invalidate();

					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			}
		});

		thread.start();
	}

	public void setPrecision(int precision) {
		this.precision = precision;
	}

	public int getPrecision() {
		return precision;
	}

}
