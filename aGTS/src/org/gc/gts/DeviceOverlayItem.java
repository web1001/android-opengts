package org.gc.gts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.overlay.OpenStreetMapViewOverlayItem;

import android.util.Log;

public class DeviceOverlayItem extends OpenStreetMapViewOverlayItem implements
		GtsConstants {

	private DeviceOverlayItem(String aTitle, String aDescription,
			GeoPoint aGeoPoint) {
		super(aTitle, aDescription, aGeoPoint);
	}

	public static DeviceOverlayItem getInstance(OpenStreetMapView osmv,
			String server, String account, String user, String password,
			String device) {
		DeviceOverlayItem deviceItem = null;

		try {
			URL url = new URL(server + "/traseu.kml?a=" + account + "&u="
					+ user + "&p=" + password + "&d=" + device + "&l=1");

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					url.openStream()));

			String line;

			double lon = 0, lat = 0;

			while ((line = reader.readLine()) != null) {
				if (line.trim().startsWith("<Point><coordinates>")) {
					line = line.substring(line.indexOf("es>") + 3);

					lon = Double.parseDouble(line.substring(0, line
							.indexOf(',')));

					line = line.substring(line.indexOf(',') + 1);

					lat = Double.parseDouble(line.substring(0, line
							.indexOf(',')));
				}
			}

			deviceItem = new DeviceOverlayItem(device, "details to come",
					new GeoPoint(lat, lon));

			reader.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

		deviceItem.startThread(osmv, server, account, user, password, device);

		return deviceItem;
	}

	public void startThread(final OpenStreetMapView osmv, final String server,
			final String account, final String user, final String password,
			final String device) {
		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(7 * MINUTE);

						Log.i("device", "awake");

						URL url = new URL(server + "/traseu.kml?a=" + account
								+ "&u=" + user + "&p=" + password + "&d="
								+ device + "&l=1");

						BufferedReader reader = new BufferedReader(
								new InputStreamReader(url.openStream()));

						String line;

						double lon = 0, lat = 0;

						while ((line = reader.readLine()) != null) {
							if (line.trim().startsWith("<Point><coordinates>")) {
								line = line.substring(line.indexOf("es>") + 3);

								lon = Double.parseDouble(line.substring(0, line
										.indexOf(',')));

								line = line.substring(line.indexOf(',') + 1);

								lat = Double.parseDouble(line.substring(0, line
										.indexOf(',')));
							}
						}

						Log.i("device", "lat=" + lat + ", lon=" + lon);

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

}
