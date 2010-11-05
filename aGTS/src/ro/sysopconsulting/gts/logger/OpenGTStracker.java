package ro.sysopconsulting.gts.logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.gc.gts.R;

import ro.sysopconsulting.OpenGTSRemote;
import ro.sysopconsulting.util.AppException;
import ro.sysopconsulting.util.Const;
import ro.sysopconsulting.util.NetworkScanReceiver;
import ro.sysopconsulting.util.StatusCodes;
import ro.sysopconsulting.util.WifiStateReceiver;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class OpenGTStracker extends Service {

    protected String TAG = "OpenGTStracker";

    protected int mLoggingState;
    // protected Object mPrecision;

    private static final int LOGGING_FINE = 0;
    private static final int LOGGING_NORMAL = 1;
    private static final int LOGGING_COARSE = 2;
    private static final int LOGGING_GLOBAL = 3;
    private static final int MAX_REASONABLE_SPEED = 60;

    private Context mContext;
    private LocationManager mLocationManager;

    private NotificationManager mNotificationManager;
    private int mSatellites = 0;
    private float mMaxAcceptableAccuracy = 5000;

    private LinkedList<Location> mLocationTrack = new LinkedList<Location>();
    private Location mPreviousLocation;
    private Notification mNotification;
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    private static final int HTTP_STATUS_OK = 200;
    private static String sUserAgent = "DroidGTSLogger1.0";

    private static String mHeaderValue;

    private int mPrecision;
    private SharedPreferences mSharedPreferences;
    private String mImei;
    private boolean mUse_Wifi = true;
    private boolean mSpeedSanityCheck = true;

    private WifiStateReceiver mWifiStateReceiver;
    private NetworkScanReceiver mNetworkScanReceiver;

    private String account = "sysop";
    private String server = "www.dantek-gpstrack.ro";
    private String servletPath = "/gprmc";

    private int mStatusCode;

    private final Timer timer = new Timer();
    private final Handler handler = new Handler();

    private TimerTask checkLocationListener = new TimerTask() {

	@Override
	public void run() {
	    // TODO
	    if (mLoggingState != Const.LOGGING) {
		handler.post(new Runnable() {
		    public void run() {
			Log.d(TAG, "Periodic Update of Services.");
			resumeLogging();

		    }

		});
	    } else {

		Log.w(TAG, "Device Notes:" + mHeaderValue);
	    }
	}

    };

    private OnSharedPreferenceChangeListener mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
	public void onSharedPreferenceChanged(
		SharedPreferences sharedPreferences, String key) {
	    Log.d(TAG, "Shared preferences changed: " + key);
	    if (key.equals(Const.PRECISION)) {
		requestLocationUpdates();
		setupNotification();

	    } else if (key.equals(Const.USE_WIFI)) {
		mUse_Wifi = sharedPreferences.getBoolean(Const.USE_WIFI, true);
		updateWifiStateReceiver();
	    }
	}
    };

    private IBinder mBinder = new OpenGTSRemote.Stub() {
	public int loggingState() throws RemoteException {

	    return mLoggingState;
	}

	public void startLogging() throws RemoteException {
	    OpenGTStracker.this.startLogging();

	}

	public void pauseLogging() throws RemoteException {
	    OpenGTStracker.this.pauseLogging();
	}

	public void resumeLogging() throws RemoteException {
	    OpenGTStracker.this.resumeLogging();

	}

	public void stopLogging() throws RemoteException {
	    OpenGTStracker.this.stopLogging();
	}

    };

    private Listener mStatusListener = new GpsStatus.Listener() {
	int mPrevEvent = -1;

	public synchronized void onGpsStatusChanged(int event) {
	    if (mPrevEvent > 0 && event != mPrevEvent) {
		mPrevEvent = event;
		switch (event) {
		case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
		    GpsStatus status = mLocationManager.getGpsStatus(null);
		    mSatellites = 0;
		    Iterable<GpsSatellite> list = status.getSatellites();
		    for (GpsSatellite satellite : list) {
			if (satellite.usedInFix()) {
			    mSatellites++;
			}
		    }
		    Log.d(TAG, "New GPS Event: " + event);
		    updateNotification();
		    break;
		default:
		    break;
		}
	    }
	}
    };

    @Override
    public IBinder onBind(Intent arg0) {

	return this.mBinder;
    }

    protected void updateNotification() {
	// TODO Auto-generated method stub

	CharSequence contentTitle = getResources().getString(
		org.gc.gts.R.string.app_name)
		+ " - " + getResources().getString(R.string.account);

	String precision = getResources().getStringArray(
		org.gc.gts.R.array.precision_choices)[mPrecision];
	String state = getResources().getStringArray(
		org.gc.gts.R.array.state_choices)[mLoggingState - 1];
	CharSequence contentText;
	switch (mPrecision) {
	case (LOGGING_GLOBAL):
	    contentText = getResources().getString(
		    R.string.service_networkstatus, state, precision);
	    break;
	default:
	    contentText = getResources().getString(R.string.service_gpsstatus,
		    state, precision, mSatellites);
	    break;
	}
	Intent notificationIntent = new Intent(this,
		org.gc.gts.GtsActivity.class);

	PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
		notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK);
	mNotification.setLatestEventInfo(this, contentTitle, contentText,
		contentIntent);

	mNotificationManager.notify(R.layout.main, mNotification);
	Log.d(TAG, "updateNotification: " + mNotification);
    }

    protected void stopLogging() {
	// TODO Auto-generated method stub
	Log.d(TAG, "stopLogging");
	this.mLoggingState = Const.STOPPED;
	updateWifiLock();
	updateWakeLock();
	updateNotification();

    }

    protected void resumeLogging() {
	// TODO Auto-generated method stub
	Log.d(TAG, "resumeLogging");
	this.mLoggingState = Const.LOGGING;

	updateWifiLock();
	updateWakeLock();
	updateNotification();
    }

    protected void pauseLogging() {
	// TODO Auto-generated method stub
	Log.d(TAG, "pauseLogging");
	this.mLoggingState = Const.PAUSED;
	updateWifiLock();
	updateWakeLock();
	updateNotification();

    }

    protected void startLogging() {
	// TODO Auto-generated method stub
	Log.d(TAG, "startLogging");
	requestLocationUpdates();
	this.mLocationManager.addGpsStatusListener(this.mStatusListener);
	this.mLoggingState = Const.LOGGING;
	updateWakeLock();
	updateWifiLock();

	setupNotification();

    }

    private void setupNotification() {
	// TODO Auto-generated method stub
	Log.d(TAG, "setupNotification");
	mNotificationManager.cancel(org.gc.gts.R.layout.main);

	int icon = org.gc.gts.R.drawable.small_icon;
	CharSequence tickerText = getResources().getString(
		org.gc.gts.R.string.service_start);
	long when = System.currentTimeMillis();

	mNotification = new Notification(icon, tickerText, when);
	mNotification.flags |= Notification.FLAG_ONGOING_EVENT;

	updateNotification();
    }

    private void updateWakeLock() {
	// TODO Auto-generated method stub
	Log.d(TAG, "updateWakeLock");
	if (this.mLoggingState == Const.LOGGING) {
	    // PreferenceManager.getDefaultSharedPreferences( this.mContext
	    // ).registerOnSharedPreferenceChangeListener(
	    // mSharedPreferenceChangeListener );
	    PowerManager pm = (PowerManager) this.mContext
		    .getSystemService(Context.POWER_SERVICE);
	    this.mWakeLock = pm
		    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
	    this.mWakeLock.acquire();
	} else {
	    if (this.mWakeLock != null) {
		this.mWakeLock.release();
		this.mWakeLock = null;
	    }
	}
    }

    private void updateWifiLock() {
	// TODO Auto-generated method stub

	if (this.mLoggingState == Const.LOGGING) {
	    // PreferenceManager.getDefaultSharedPreferences( this.mContext
	    // ).registerOnSharedPreferenceChangeListener(
	    // mSharedPreferenceChangeListener );
	    WifiManager wm = (WifiManager) this.mContext
		    .getSystemService(Context.WIFI_SERVICE);
	    this.mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
	    if (this.mWifiLock != null) {
		this.mWifiLock.acquire();
		Log.d(TAG, "updateWifiLock to Acquire");
	    }

	} else {
	    if (this.mWifiLock != null) {
		this.mWifiLock.release();
		this.mWifiLock = null;
		Log.d(TAG, "updateWifiLock to release");
	    }
	}
    }

    private void requestLocationUpdates() {
	// TODO Auto-generated method stub
	Log.d(TAG, "requestLocationUpdates");
	this.mLocationManager.removeUpdates(this.mLocationListener);
	mPrecision = new Integer(PreferenceManager.getDefaultSharedPreferences(
		this.mContext).getString(Const.PRECISION, "1")).intValue();
	Log.d(TAG, "requestLocationUpdates to precision " + mPrecision);
	switch (mPrecision) {
	case (LOGGING_FINE): // Fine
	    mLocationManager.requestLocationUpdates(
		    LocationManager.GPS_PROVIDER, 3000l, 5F,
		    this.mLocationListener);
	    mMaxAcceptableAccuracy = 20f;

	    break;
	case (LOGGING_NORMAL): // Normal
	    mLocationManager.requestLocationUpdates(
		    LocationManager.GPS_PROVIDER, 15000l, 10F,
		    this.mLocationListener);
	    mMaxAcceptableAccuracy = 50f;
	    break;
	case (LOGGING_COARSE): // Coarse
	    mLocationManager.requestLocationUpdates(
		    LocationManager.GPS_PROVIDER, 30000l, 25F,
		    this.mLocationListener);
	    mMaxAcceptableAccuracy = 200f;
	    break;
	case (LOGGING_GLOBAL): // Global
	    mLocationManager.requestLocationUpdates(
		    LocationManager.NETWORK_PROVIDER, 300000l, 500F,
		    this.mLocationListener);
	    mMaxAcceptableAccuracy = 5000f;
	    if (!isNetworkConnected()) {
		disabledProviderNotification(R.string.service_connectiondisabled);
	    }
	    break;
	default:
	    Log.e(TAG, "Unknown precision " + mPrecision);
	    break;
	}
    }

    private void disabledProviderNotification(int resId) {
	int icon = R.drawable.small_icon;
	CharSequence tickerText = getResources().getString(resId);
	long when = System.currentTimeMillis();
	Notification gpsNotification = new Notification(icon, tickerText, when);
	gpsNotification.flags |= Notification.FLAG_AUTO_CANCEL;

	CharSequence contentTitle = getResources().getString(R.string.app_name)
		+ " - " + getResources().getString(R.string.account);
	CharSequence contentText = getResources().getString(resId);
	Intent notificationIntent = new Intent(this,
		org.gc.gts.GtsActivity.class);
	// notificationIntent.setData( ContentUris.withAppendedId(
	// Tracks.CONTENT_URI, mTrackId ) );
	PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
		notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK);
	gpsNotification.setLatestEventInfo(this, contentTitle, contentText,
		contentIntent);

	mNotificationManager.notify(R.drawable.icon, gpsNotification);
    }

    private LocationListener mLocationListener = new LocationListener() {
	public void onLocationChanged(Location location) {
	    Log.d(TAG, "onLocationChanged");
	    Location filteredLocation = locationFilter(location);
	    if (filteredLocation != null) {

		SendLocation(filteredLocation);
	    }
	}

	public void onProviderDisabled(String provider) {
	    Log.d(TAG, "onProviderDisabled: " + provider);
	    if (provider.equals("gps")) {
		// switching to
		// network
		// location
		// provider
		mPrecision = LOGGING_GLOBAL;
		Editor pfEditor = PreferenceManager
			.getDefaultSharedPreferences(mContext).edit();
		pfEditor.putString(Const.PRECISION,
			Integer.toString(LOGGING_GLOBAL));
		pfEditor.commit();
		// requestLocationUpdates();
	    }

	}

	public void onProviderEnabled(String provider) {
	    Log.d(TAG, "onProviderEnabled: " + provider);

	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
	    Log.w(TAG, String.format("Provider %s changed to status %d",
		    provider, status));
	}
    };

    public Location locationFilter(Location proposedLocation) {
	Log.d(TAG, "locationFilter()");

	if (mPreviousLocation == null) {
	    mPreviousLocation = proposedLocation;
	    return proposedLocation;
	}

	if (proposedLocation != null
		&& (proposedLocation.getAccuracy() > mMaxAcceptableAccuracy)) {
	    Log.w(TAG,
		    String.format(
			    "A weak location was recieved, lots of inaccuracy... (%f is more then max %f)",
			    proposedLocation.getAccuracy(),
			    mMaxAcceptableAccuracy));
	    return null;
	}

	// Do not log a waypoint which might be on any side of the previous
	// waypoint
	if (proposedLocation != null
		&& mPreviousLocation != null
		&& (proposedLocation.getAccuracy() > mPreviousLocation
			.distanceTo(proposedLocation))) {
	    Log.w(TAG,
		    String.format(
			    "A weak location was recieved, not quite clear from the previous waypoint... (%f more then max %f)",
			    proposedLocation.getAccuracy(),
			    mPreviousLocation.distanceTo(proposedLocation)));
	    mPreviousLocation = proposedLocation;
	    return null;
	}

	// Speed checks for NETWORK logging, check if the proposed location
	// could be reached from the previous one in sane speed
	if (mSpeedSanityCheck && proposedLocation != null
		&& mPreviousLocation != null) {
	    // To avoid near instant teleportation on network location or
	    // glitches cause continent hopping
	    float meters = proposedLocation.distanceTo(mPreviousLocation);
	    long seconds = (proposedLocation.getTime() - mPreviousLocation
		    .getTime()) / 1000L;

	    if (meters / seconds > MAX_REASONABLE_SPEED) {
		Log.w(TAG,
			"A strange location was recieved, a really high speed, prob wrong..."
				+ proposedLocation.toString());
		return null;
	    }
	}
	mPreviousLocation = proposedLocation;
	return proposedLocation;

    }

    protected void SendLocation(Location loc) {
	// TODO Auto-generated method stub
	Log.d(TAG, "SendLocation");
	// check IMEI - maybe is a special device
	boolean ex = false;
	// test if is data in queue, send it
	// aici se trimit datele catre serverul OpenGTS, deocamdata pe HTTP
	int size = mLocationTrack.size();

	while (size > 0) {

	    String url = getLocationURL(mLocationTrack.peek());
	    mStatusCode = StatusCodes.STATUS_WAYMARK_0;

	    try {
		ex = false;
		--size;
		setOpenGTSLocation(url);

	    } catch (ro.sysopconsulting.util.AppException e) {
		Log.w(TAG, "Error sending from queue: " + url);
		mStatusCode = StatusCodes.STATUS_WAYMARK_1;
		ex = true;
		size = 0;

	    }
	    if (!ex) {
		Log.w(TAG, "Sent from queue: " + url);
		mLocationTrack.remove();

	    }
	}
	if (loc != null) {
	    String url = getLocationURL(loc);
	    // start here a new background task?
	    try {
		mStatusCode = StatusCodes.STATUS_LOCATION;
		setOpenGTSLocation(url);
	    } catch (ro.sysopconsulting.util.AppException e) {
		// error, put the data in queue
		e.printStackTrace();
		Log.w(TAG, "Error, adding to queue: " + loc.toString());
		mLocationTrack.add(loc);
		Log.w(TAG, "QSize= " + mLocationTrack.size());
	    }
	}
    }

    private String getLocationURL(Location loc) {
	// TODO Auto-generated method stub
	String device = mImei;
	if (device == null)
	    device = "android";

	String url = "http://" + server + servletPath + "/Data?acct=" + account
		+ "&dev=" + device;
	url = url + "&lon=" + loc.getLongitude() + "&lat=" + loc.getLatitude()
		+ "&head=" + loc.getBearing() + "&speed="
		+ (loc.getSpeed() * 3.6) + "&alt=" + loc.getAltitude()
		+ "&time=" + loc.getTime() + "&code=" + mStatusCode;
	Log.w(TAG, "getLocationURL: " + url);

	return url;
    }

    protected static synchronized void setOpenGTSLocation(String url)
	    throws AppException {

	// Create client and set our specific user-agent string
	HttpClient client = new DefaultHttpClient();
	HttpGet request = new HttpGet(url);
	request.setHeader("User-Agent", sUserAgent);

	try {
	    HttpResponse response = client.execute(request);

	    // Check if server response is valid
	    StatusLine status = response.getStatusLine();

	    if (status.getStatusCode() != HTTP_STATUS_OK) {
		throw new AppException("Invalid response from server: "
			+ status.toString());
	    }

	    Header header = response.getLastHeader("DeviceNotes");
	    mHeaderValue = header.getValue();

	} catch (IOException e) {
	    throw new AppException("Problem communicating with API", e);
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
	// TODO Auto-generated method stub
	Log.d(TAG, "onCreate()");
	super.onCreate();
	TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
	mImei = telephonyManager.getDeviceId();
	account = getResources().getString(R.string.account);
	server = getResources().getString(R.string.server);

	mLoggingState = Const.STOPPED;
	mContext = getApplicationContext();
	mSharedPreferences = PreferenceManager
		.getDefaultSharedPreferences(this);
	mSharedPreferences
		.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
	// SharedPreferences sharedPreferences =
	// PreferenceManager.getDefaultSharedPreferences( this.mContext );
	mLocationManager = (LocationManager) this.mContext
		.getSystemService(Context.LOCATION_SERVICE);
	mNotificationManager = (NotificationManager) this.mContext
		.getSystemService(Context.NOTIFICATION_SERVICE);
	mNotificationManager.cancelAll();

	mUse_Wifi = mSharedPreferences.getBoolean(Const.USE_WIFI, true);

	mLocationTrack = new LinkedList<Location>();
	mStatusCode = StatusCodes.STATUS_LOCATION;
	// check status every 1 minute, after 5 minutes from start
	timer.schedule(checkLocationListener, 1000 * 60, 1000 * 60);
	updateWifiStateReceiver();

	startLogging();

    }

    private void updateWifiStateReceiver() {
	// TODO Auto-generated method stub
	if (mUse_Wifi) {
	    mWifiStateReceiver = new WifiStateReceiver();
	    IntentFilter intentFilter = new IntentFilter();
	    intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
	    intentFilter
		    .addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
	    intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
	    mContext.registerReceiver(mWifiStateReceiver, intentFilter);
	    mNetworkScanReceiver = new NetworkScanReceiver();
	    IntentFilter scanFilter = new IntentFilter();
	    scanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
	    mContext.registerReceiver(mNetworkScanReceiver, scanFilter);
	    Log.d(TAG, "Wifi receivers registered.");
	} else {
	    try {
		mContext.unregisterReceiver(mNetworkScanReceiver);
		mContext.unregisterReceiver(mWifiStateReceiver);
		Log.d(TAG, "Wifi receivers Unregistered.");
	    } catch (Exception e) {

		Log.d(TAG, "Unregister exception, already unregistered");
	    }
	}

    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
	// TODO Auto-generated method stub
	stopLogging();

	mSharedPreferences
		.unregisterOnSharedPreferenceChangeListener(this.mSharedPreferenceChangeListener);
	if (mUse_Wifi) {

	    mContext.unregisterReceiver(mWifiStateReceiver);
	}

	super.onDestroy();
    }

    private boolean isNetworkConnected() {
	ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	NetworkInfo info = connMgr.getActiveNetworkInfo();

	return (info != null && info.isConnected());
    }

}
