package org.gc.gts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.overlay.OpenStreetMapViewItemizedOverlay;

import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

public class DevicesOverlay extends
		OpenStreetMapViewItemizedOverlay<DeviceOverlayItem> {

	protected DevicesOverlay(Context ctx, List<DeviceOverlayItem> aList,
			Drawable pMarker, Point pMarkerHotspot,
			OnItemTapListener<DeviceOverlayItem> aOnItemTapListener) {
		super(ctx, aList, pMarker, pMarkerHotspot, aOnItemTapListener, null);
	}

	public static DevicesOverlay getInstance(final Context ctx,
			OpenStreetMapView osmv, String server, String account, String user,
			String password) {
		final ArrayList<DeviceOverlayItem> devices = new ArrayList<DeviceOverlayItem>();

		try {
			URL url = new URL(server + "/Data.kml?a=" + account + "&u=" + user
					+ "&p=" + password + "&l=1&g=all");

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					url.openStream()));

			String line;

			while ((line = reader.readLine()) != null) {
				if (line.trim().startsWith("<name>")) {
					line = line.substring(line.indexOf("me>") + 3);

					String name = line.substring(0, line.indexOf('<'));
					Log.d("aGTS", "Found device " + name);
					devices.add(DeviceOverlayItem.getInstance(osmv, server,
							account, user, password, name));
				}
			}

			// String[] values = line.split(",");

			// int nr = Integer.parseInt(values[0]);

			// for (int i = 1; i <= nr; i++)
			// devices.add(DeviceOverlayItem.getInstance(osmv, server,
			// account, user, password, values[i]));

			reader.close();

		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return new DevicesOverlay(ctx, devices, ctx.getResources().getDrawable(
				R.drawable.car), new Point(8, 8),
				new DevicesOverlay.OnItemTapListener<DeviceOverlayItem>() {
					@Override
					public boolean onItemTap(int index, DeviceOverlayItem item) {
						Log.i("aGTS", "" + item.mTitle + ": "
								+ item.mDescription);
						Toast.makeText(ctx,
								"" + item.mTitle + ": " + item.mDescription,
								Toast.LENGTH_LONG).show();
						return true; // We 'handled' this event.
					}
				});
	}

}
