package org.gc.gts;

import java.util.ArrayList;
import java.util.List;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.OpenStreetMapView.OpenStreetMapViewProjection;
import org.andnav.osm.views.overlay.OpenStreetMapViewItemizedOverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

public class LatitudeOverlay extends
		OpenStreetMapViewItemizedOverlay<LatitudeOverlayItem> {

	private LatitudeOverlay(Context ctx, List<LatitudeOverlayItem> aList,
			Drawable pMarker, Point pMarkerHotspot,
			OnItemTapListener<LatitudeOverlayItem> aOnItemTapListener) {
		super(ctx, aList, pMarker, pMarkerHotspot, aOnItemTapListener, null);
	}

	public static LatitudeOverlay getInstance(final Context ctx,
			OpenStreetMapView osmv, String user) {
		final ArrayList<LatitudeOverlayItem> latitude = new ArrayList<LatitudeOverlayItem>();

		LatitudeOverlayItem item = LatitudeOverlayItem.getInstance(osmv, user);

		if (null != item)
			latitude.add(item);

		return new LatitudeOverlay(ctx, latitude, ctx.getResources()
				.getDrawable(R.drawable.phone), new Point(8, 8),
				new LatitudeOverlay.OnItemTapListener<LatitudeOverlayItem>() {
					public boolean onItemTap(int index, LatitudeOverlayItem item) {
						Log.i("aGTS", "" + item.mTitle + ": "
								+ item.mDescription);
						Toast.makeText(ctx,
								"" + item.mTitle + ": " + item.mDescription,
								Toast.LENGTH_LONG).show();
						return true; // We 'handled' this event.
					}
				});
	}

	public void onDraw(final Canvas c, final OpenStreetMapView mapView) {
		final OpenStreetMapViewProjection pj = mapView.getProjection();

		final Point curScreenCoords = new Point();

		Paint paintPrecision = new Paint();
		paintPrecision.setColor(Color.GREEN);
		paintPrecision.setAlpha(30);

		float radius = 0;

		/*
		 * Draw in backward cycle, so the items with the least index are on the
		 * front.
		 */
		for (int i = this.mItemList.size() - 1; i >= 0; i--) {
			LatitudeOverlayItem item = this.mItemList.get(i);

			pj.toMapPixels(item.mGeoPoint, curScreenCoords);

			radius = pj.metersToEquatorPixels(item.getPrecision());

			c.drawCircle(curScreenCoords.x, curScreenCoords.y, radius,
					paintPrecision);

			paintPrecision.setStyle(Style.STROKE);
			paintPrecision.setStrokeWidth(2);
			paintPrecision.setAlpha(200);

			c.drawCircle(curScreenCoords.x, curScreenCoords.y, radius,
					paintPrecision);

			onDrawItem(c, i, curScreenCoords);
		}
	}

	public GeoPoint getLocation() {
		return this.mItemList.get(0).mGeoPoint;
	}
	
}
