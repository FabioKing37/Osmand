package net.osmand.plus.activities;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.osmand.Location;
import net.osmand.StateChangedListener;
import net.osmand.access.AccessibilityPlugin;
import net.osmand.access.AccessibleActivity;
import net.osmand.access.AccessibleAlertBuilder;
import net.osmand.access.AccessibleToast;
import net.osmand.access.MapAccessibilityActions;
import net.osmand.data.LatLon;
import net.osmand.data.QuadPoint;
import net.osmand.data.RotatedTileBox;
import net.osmand.map.MapTileDownloader.DownloadRequest;
import net.osmand.map.MapTileDownloader.IMapDownloaderCallback;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.BusyIndicator;
import net.osmand.plus.OsmAndConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.OsmandSettings.AutoZoomMap;
import net.osmand.plus.PoiFilter;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.Version;
import net.osmand.plus.activities.actions.NavigateAction;
import net.osmand.plus.activities.actions.NavigateAction.DirectionDialogStyle;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.activities.search.SearchPOIActivity;
import net.osmand.plus.base.FailSafeFuntions;
import net.osmand.plus.base.MapViewTrackingUtilities;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.plus.render.RendererRegistry;
import net.osmand.plus.resources.ResourceManager;
import net.osmand.plus.routing.RouteProvider.GPXRouteParams;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.RoutingHelper.RouteCalculationProgressCallback;
import net.osmand.plus.views.AnimateDraggingMapThread;
import net.osmand.plus.views.OsmandMapLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.util.Algorithms;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MapActivity extends AccessibleActivity {

	private static final int SHOW_POSITION_MSG_ID = OsmAndConstants.UI_HANDLER_MAP_VIEW + 1;
	private static final int LONG_KEYPRESS_MSG_ID = OsmAndConstants.UI_HANDLER_MAP_VIEW + 2;
	private static final int LONG_KEYPRESS_DELAY = 500;

	private static final String EXCEPTION_FILE_SIZE = "EXCEPTION_FS"; //$NON-NLS-1$
	private static final String VECTOR_INDEXES_CHECK = "VECTOR_INDEXES_CHECK"; //$NON-NLS-1$

	private static MapViewTrackingUtilities mapViewTrackingUtilities;

	/** Called when the activity is first created. */
	OsmandMapTileView mapView;

	private MapActivityActions mapActions;
	private MapActivityLayers mapLayers;

	// Notification status
	private NotificationManager mNotificationManager;
	private int APP_NOTIFICATION_ID = 1;

	// handler to show/hide trackball position and to link map with delay
	private Handler uiHandler = new Handler();
	// App variables
	private OsmandApplication app;
	private OsmandSettings settings;

	private Dialog progressDlg = null;

	private ProgressDialog startProgressDialog;
	private List<DialogProvider> dialogProviders = new ArrayList<DialogProvider>(
			2);
	private StateChangedListener<ApplicationMode> applicationModeListener;
	private FrameLayout lockView;

	private Notification getNotification() {
		Intent notificationIndent = new Intent(this,
				OsmandIntents.getMapActivity());
		notificationIndent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		Notification notification = new Notification(R.drawable.icon, "", //$NON-NLS-1$
				System.currentTimeMillis());
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(this, Version.getAppName(getApp()),
				getString(R.string.go_back_to_osmand), PendingIntent
						.getActivity(this, 0, notificationIndent,
								PendingIntent.FLAG_UPDATE_CURRENT));
		return notification;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setApp(getMyApplication());
		settings = getApp().getSettings();
		getApp().applyTheme(this);
		super.onCreate(savedInstanceState);

		mapActions = new MapActivityActions(this);
		mapLayers = new MapActivityLayers(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// Full screen is not used here
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		// WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.main);
		startProgressDialog = new ProgressDialog(this);
		startProgressDialog.setCancelable(true);
		getApp().checkApplicationIsBeingInitialized(this, startProgressDialog);
		parseLaunchIntentLocation();

		checkVectorIndexesDownloaded();

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			Boolean firstTime = extras.getBoolean("FIRSTTIME");

			checkPreviousRunsForExceptions(firstTime);
		}

		mapView = (OsmandMapTileView) findViewById(R.id.MapView);
		mapView.setTrackBallDelegate(new OsmandMapTileView.OnTrackBallListener() {
			@Override
			public boolean onTrackBallEvent(MotionEvent e) {
				showAndHideMapPosition();
				return MapActivity.this.onTrackballEvent(e);
			}
		});
		mapView.setAccessibilityActions(new MapAccessibilityActions(this));
		if (mapViewTrackingUtilities == null) {
			mapViewTrackingUtilities = new MapViewTrackingUtilities(getApp());
		}
		mapViewTrackingUtilities.setMapView(mapView);

		// Do some action on close
		startProgressDialog
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						getApp().getResourceManager().getRenderer()
								.clearCache();
						mapView.refreshMap(true);
					}
				});

		// TODO: Ver o que faz? - Pede para sacar mapas?
		getApp().getResourceManager().getMapTileDownloader()
				.addDownloaderCallback(new IMapDownloaderCallback() {
					@Override
					public void tileDownloaded(DownloadRequest request) {
						if (request != null && !request.error
								&& request.fileToSave != null) {
							ResourceManager mgr = getApp().getResourceManager();
							mgr.tileDownloaded(request);
						}
						if (request == null || !request.error) {
							mapView.tileDownloaded(request);
						}
					}
				});
		// TODO: Aqui mete a progressBar?
		createProgressBarForRouting();
		mapLayers.createLayers(mapView);
		// This situtation could be when navigation suddenly crashed and after
		// restarting
		// it tries to continue the last route
		if (settings.FOLLOW_THE_ROUTE.get()
				&& !getApp().getRoutingHelper().isRouteCalculated()
				&& !getApp().getRoutingHelper().isRouteBeingCalculated()) {
			FailSafeFuntions.restoreRoutingMode(this);
		}

		if (!settings.isLastKnownMapLocation()) {
			// show first time when application ran
			net.osmand.Location location = getApp().getLocationProvider()
					.getFirstTimeRunDefaultLocation();
			if (location != null) {
				mapView.setLatLon(location.getLatitude(),
						location.getLongitude());
				mapView.setIntZoom(14);
			}
		}
		addDialogProvider(mapActions);
		OsmandPlugin.onMapActivityCreate(this);
		if (lockView != null) {
			((FrameLayout) mapView.getParent()).addView(lockView);
		}
	}

	protected void checkVectorIndexesDownloaded() {
		MapRenderRepositories maps = getMyApplication().getResourceManager()
				.getRenderer();
		SharedPreferences pref = getPreferences(MODE_WORLD_WRITEABLE);
		boolean check = pref.getBoolean(VECTOR_INDEXES_CHECK, true);
		// do not show each time
		if (check && new Random().nextInt() % 5 == 1) {
			Builder builder = new AccessibleAlertBuilder(this);
			if (maps.isEmpty()) {
				builder.setMessage(R.string.vector_data_missing);
			} else if (!maps.basemapExists()) {
				builder.setMessage(R.string.basemap_missing);
			} else {
				return;
			}
			builder.setPositiveButton(R.string.download_files,
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							startActivity(new Intent(MapActivity.this,
									DownloadIndexActivity.class));
						}

					});
			builder.setNeutralButton(R.string.vector_map_not_needed,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							getPreferences(MODE_WORLD_WRITEABLE).edit()
									.putBoolean(VECTOR_INDEXES_CHECK, false)
									.commit();
						}
					});
			builder.setNegativeButton(R.string.first_time_continue, null);
			builder.show();
		}

	}

	public void checkPreviousRunsForExceptions(boolean firstTime) {
		long size = getPreferences(MODE_WORLD_READABLE).getLong(
				EXCEPTION_FILE_SIZE, 0);
		final OsmandApplication app = ((OsmandApplication) getApplication());
		final File file = app.getAppPath(OsmandApplication.EXCEPTION_PATH);
		if (file.exists() && file.length() > 0) {
			if (size != file.length() && !firstTime) {
				String msg = MessageFormat.format(
						getString(R.string.previous_run_crashed),
						OsmandApplication.EXCEPTION_PATH);
				Builder builder = new AccessibleAlertBuilder(MapActivity.this);
				builder.setMessage(msg).setNeutralButton(
						getString(R.string.close), null);
				builder.setPositiveButton(R.string.send_report,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								Intent intent = new Intent(Intent.ACTION_SEND);
								intent.putExtra(Intent.EXTRA_EMAIL,
										new String[] { "osmand.app@gmail.com" }); //$NON-NLS-1$
								intent.putExtra(Intent.EXTRA_STREAM,
										Uri.fromFile(file));
								intent.setType("vnd.android.cursor.dir/email"); //$NON-NLS-1$
								intent.putExtra(Intent.EXTRA_SUBJECT,
										"OsmAnd bug"); //$NON-NLS-1$
								StringBuilder text = new StringBuilder();
								text.append("\nDevice : ").append(Build.DEVICE); //$NON-NLS-1$
								text.append("\nBrand : ").append(Build.BRAND); //$NON-NLS-1$
								text.append("\nModel : ").append(Build.MODEL); //$NON-NLS-1$
								text.append("\nProduct : ").append(Build.PRODUCT); //$NON-NLS-1$
								text.append("\nBuild : ").append(Build.DISPLAY); //$NON-NLS-1$
								text.append("\nVersion : ").append(Build.VERSION.RELEASE); //$NON-NLS-1$
								text.append("\nApp Version : ").append(Version.getAppName(app)); //$NON-NLS-1$
								try {
									PackageInfo info = getPackageManager()
											.getPackageInfo(getPackageName(), 0);
									if (info != null) {
										text.append("\nApk Version : ").append(info.versionName).append(" ").append(info.versionCode); //$NON-NLS-1$ //$NON-NLS-2$
									}
								} catch (NameNotFoundException e) {
								}
								intent.putExtra(Intent.EXTRA_TEXT,
										text.toString());
								startActivity(Intent.createChooser(intent,
										getString(R.string.send_report)));
							}

						});
				builder.show();
			}
			getPreferences(MODE_WORLD_WRITEABLE).edit()
					.putLong(EXCEPTION_FILE_SIZE, file.length()).commit();
		} else {
			if (size > 0) {
				getPreferences(MODE_WORLD_WRITEABLE).edit()
						.putLong(EXCEPTION_FILE_SIZE, 0).commit();
			}
		}
	}

	public void addLockView(FrameLayout lockView) {
		this.lockView = lockView;
	}

	private void createProgressBarForRouting() {
		FrameLayout parent = (FrameLayout) mapView.getParent();
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
				Gravity.CENTER_HORIZONTAL | Gravity.TOP);
		DisplayMetrics dm = getResources().getDisplayMetrics();
		params.topMargin = (int) (60 * dm.density);
		final ProgressBar pb = new ProgressBar(this, null,
				android.R.attr.progressBarStyleHorizontal);
		pb.setIndeterminate(false);
		pb.setMax(200);
		pb.setLayoutParams(params);
		pb.setVisibility(View.GONE);

		parent.addView(pb);
		getApp().getRoutingHelper().setProgressBar(
				new RouteCalculationProgressCallback() {

					@Override
					public void updateProgress(int progress) {
						pb.setVisibility(View.VISIBLE);
						pb.setProgress(progress);

					}

					@Override
					public void finish() {
						pb.setVisibility(View.GONE);
					}
				});
	}

	@SuppressWarnings("rawtypes")
	public Object getLastNonConfigurationInstanceByKey(String key) {
		Object k = super.getLastNonConfigurationInstance();
		if (k instanceof Map) {
			return ((Map) k).get(key);
		}
		return null;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		LinkedHashMap<String, Object> l = new LinkedHashMap<String, Object>();
		for (OsmandMapLayer ml : mapView.getLayers()) {
			ml.onRetainNonConfigurationInstance(l);
		}
		return l;
	}

	@Override
	protected void onResume() {
		super.onResume();
		cancelNotification();
		if (settings.MAP_SCREEN_ORIENTATION.get() != getRequestedOrientation()) {
			setRequestedOrientation(settings.MAP_SCREEN_ORIENTATION.get());
			// can't return from this method we are not sure if activity will be
			// recreated or not
		}

		getApp().getLocationProvider().checkIfLastKnownLocationIsValid();
		// for voice navigation
		if (settings.AUDIO_STREAM_GUIDANCE.get() != null) {
			setVolumeControlStream(settings.AUDIO_STREAM_GUIDANCE.get());
		} else {
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}

		applicationModeListener = new StateChangedListener<ApplicationMode>() {
			@Override
			public void stateChanged(ApplicationMode change) {
				updateApplicationModeSettings();
			}
		};
		settings.APPLICATION_MODE.addListener(applicationModeListener);
		updateApplicationModeSettings();

		String filterId = settings.getPoiFilterForMap();
		PoiFilter poiFilter = getApp().getPoiFilters().getFilterById(filterId);
		if (poiFilter == null) {
			poiFilter = new PoiFilter(null, getApp());
		}

		mapLayers.getPoiMapLayer().setFilter(poiFilter);

		// if destination point was changed try to recalculate route
		TargetPointsHelper targets = getApp().getTargetPointsHelper();
		RoutingHelper routingHelper = getApp().getRoutingHelper();
		if (routingHelper.isFollowingMode()
				&& (!Algorithms.objectEquals(targets.getPointToNavigate(),
						routingHelper.getFinalLocation()) || !Algorithms
						.objectEquals(targets.getIntermediatePoints(),
								routingHelper.getIntermediatePoints()))) {
			routingHelper.setFinalAndCurrentLocation(targets
					.getPointToNavigate(), targets.getIntermediatePoints(),
					getApp().getLocationProvider().getLastKnownLocation(),
					routingHelper.getCurrentGPXRoute());
		}
		getApp().getLocationProvider().resumeAllUpdates();

		if (settings != null && settings.isLastKnownMapLocation()) {
			LatLon l = settings.getLastKnownMapLocation();
			mapView.setLatLon(l.getLatitude(), l.getLongitude());
			mapView.setIntZoom(settings.getLastKnownMapZoom());
		}

		settings.MAP_ACTIVITY_ENABLED.set(true);
		checkExternalStorage();
		showAndHideMapPosition();

		LatLon cur = new LatLon(mapView.getLatitude(), mapView.getLongitude());
		LatLon latLonToShow = settings.getAndClearMapLocationToShow();
		String mapLabelToShow = settings.getAndClearMapLabelToShow();
		Object toShow = settings.getAndClearObjectToShow();

		// TODO: Se o Dialog Estiver aberto?
		if (settings.isRouteToPointNavigateAndClear()) {
			// always enable and follow and let calculate it (GPS is not
			// accessible in garage)
			Location loc = new Location("map");
			if (latLonToShow != null) {
				loc.setLatitude(latLonToShow.getLatitude());
				loc.setLongitude(latLonToShow.getLongitude());
			} else {
				loc.setLatitude(mapView.getLatitude());
				loc.setLongitude(mapView.getLongitude());
			}

			Intent intent = getIntent();
			Bundle b = intent.getExtras();
			Bundle b1 = getIntent().getExtras();
			Bundle bundle = this.getIntent().getExtras();

			int value = b1.getInt("state", 0);

			String name = "teste";
			String data = "teste";

			if (b != null) {

				name = bundle.getString("name");
				data = bundle.getString(SearchPOIActivity.POI_NAME);
				if (SearchPOIActivity.POI_NAME == null)
					data = "DEU NULL";
				int value1 = value;
			}

			new NavigateAction(this).getDirections(loc, data, null,
					DirectionDialogStyle.create());
		}
		if (mapLabelToShow != null && latLonToShow != null) {
			mapLayers.getContextMenuLayer().setSelectedObject(toShow);
			mapLayers.getContextMenuLayer().setLocation(latLonToShow,
					mapLabelToShow);
		}
		if (latLonToShow != null && !latLonToShow.equals(cur)) {
			mapView.getAnimatedDraggingThread().startMoving(
					latLonToShow.getLatitude(), latLonToShow.getLongitude(),
					settings.getMapZoomToShow(), true);
		}
		if (latLonToShow != null) {
			// remember if map should come back to isMapLinkedToLocation=true
			mapViewTrackingUtilities.setMapLinkedToLocation(false);
		}

		View progress = mapLayers.getMapInfoLayer().getProgressBar();
		if (progress != null) {
			getApp().getResourceManager().setBusyIndicator(
					new BusyIndicator(this, progress));
		}

		OsmandPlugin.onMapActivityResume(this);
		mapView.refreshMap(true);
	}

	public OsmandApplication getMyApplication() {
		return ((OsmandApplication) getApplication());
	}

	public void addDialogProvider(DialogProvider dp) {
		dialogProviders.add(dp);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		for (DialogProvider dp : dialogProviders) {
			dialog = dp.onCreateDialog(id);
			if (dialog != null) {
				return dialog;
			}
		}
		if (id == OsmandApplication.PROGRESS_DIALOG) {
			return startProgressDialog;
		}
		return null;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		for (DialogProvider dp : dialogProviders) {
			dp.onPrepareDialog(id, dialog);
		}
	}

	public void changeZoom(int stp) {
		// delta = Math.round(delta * OsmandMapTileView.ZOOM_DELTA) *
		// OsmandMapTileView.ZOOM_DELTA_1;
		boolean changeLocation = true;
		if (settings.AUTO_ZOOM_MAP.get() == AutoZoomMap.NONE) {
			changeLocation = false;
		}
		final int newZoom = mapView.getZoom() + stp;
		mapView.getAnimatedDraggingThread().startZooming(newZoom,
				changeLocation);
		if (getApp().getInternalAPI().accessibilityEnabled())
			AccessibleToast
					.makeText(
							this,
							getString(R.string.zoomIs) + " " + newZoom, Toast.LENGTH_SHORT).show(); //$NON-NLS-1$
		showAndHideMapPosition();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
				&& getApp().getInternalAPI().accessibilityEnabled()) {
			if (!uiHandler.hasMessages(LONG_KEYPRESS_MSG_ID)) {
				Message msg = Message.obtain(uiHandler, new Runnable() {
					@Override
					public void run() {
						getApp().getLocationProvider().emitNavigationHint();
					}
				});
				msg.what = LONG_KEYPRESS_MSG_ID;
				uiHandler.sendMessageDelayed(msg, LONG_KEYPRESS_DELAY);
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_MENU
				&& event.getRepeatCount() == 0) {
			mapActions.openOptionsMenuAsList();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_SEARCH
				&& event.getRepeatCount() == 0) {
			Intent newIntent = new Intent(MapActivity.this,
					OsmandIntents.getSearchActivity());
			// causes wrong position caching:
			// newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			LatLon loc = getMapLocation();
			newIntent.putExtra(SearchActivity.SEARCH_LAT, loc.getLatitude());
			newIntent.putExtra(SearchActivity.SEARCH_LON, loc.getLongitude());
			startActivity(newIntent);
			newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			return true;
		} else if (!getApp().getRoutingHelper().isFollowingMode()
				&& OsmandPlugin.getEnabledPlugin(AccessibilityPlugin.class) != null) {
			// Find more appropriate plugin for it?
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
					&& event.getRepeatCount() == 0) {
				if (mapView.isZooming()) {
					changeZoom(+2);
				} else {
					changeZoom(+1);
				}
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
					&& event.getRepeatCount() == 0) {
				changeZoom(-1);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	public void setMapLocation(double lat, double lon) {
		mapView.setLatLon(lat, lon);
		mapViewTrackingUtilities.locationChanged(lat, lon, this);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE
				&& settings.USE_TRACKBALL_FOR_MOVEMENTS.get()) {
			float x = event.getX();
			float y = event.getY();
			final RotatedTileBox tb = mapView.getCurrentRotatedTileBox();
			final QuadPoint cp = tb.getCenterPixelPoint();
			final LatLon l = tb
					.getLatLonFromPixel(cp.x + x * 15, cp.y + y * 15);
			setMapLocation(l.getLatitude(), l.getLongitude());
			return true;
		}
		return super.onTrackballEvent(event);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	protected void setProgressDlg(Dialog progressDlg) {
		this.progressDlg = progressDlg;
	}

	protected Dialog getProgressDlg() {
		return progressDlg;
	}

	@Override
	protected void onStop() {
		if (getApp().getRoutingHelper().isFollowingMode()) {
			mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			if (mNotificationManager != null) {
				mNotificationManager.notify(APP_NOTIFICATION_ID,
						getNotification());
			}
		}
		if (progressDlg != null) {
			progressDlg.dismiss();
			progressDlg = null;
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		FailSafeFuntions.quitRouteRestoreDialog();
		OsmandPlugin.onMapActivityDestroy(this);
		mapViewTrackingUtilities.setMapView(null);
		cancelNotification();
		getApp().getResourceManager().getMapTileDownloader()
				.removeDownloaderCallback(mapView);
	}

	private void cancelNotification() {
		if (mNotificationManager == null) {
			mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		if (mNotificationManager != null) {
			mNotificationManager.cancel(APP_NOTIFICATION_ID);
		}
	}

	public void followRoute(ApplicationMode appMode, LatLon finalLocation,
			List<LatLon> intermediatePoints,
			net.osmand.Location currentLocation, GPXRouteParams gpxRoute) {
		getMapViewTrackingUtilities().backToLocationImpl();
		RoutingHelper routingHelper = getApp().getRoutingHelper();
		settings.APPLICATION_MODE.set(appMode);
		settings.FOLLOW_THE_ROUTE.set(true);
		if (gpxRoute == null) {
			settings.FOLLOW_THE_GPX_ROUTE.set(null);
		}
		routingHelper.setFollowingMode(true);
		routingHelper.setFinalAndCurrentLocation(finalLocation,
				intermediatePoints, currentLocation, gpxRoute);
		// Voice Dialog
		getApp().showDialogInitializingCommandPlayer(MapActivity.this);
	}

	public LatLon getMapLocation() {
		return new LatLon(mapView.getLatitude(), mapView.getLongitude());
	}

	// Duplicate methods to OsmAndApplication
	public LatLon getPointToNavigate() {
		return getApp().getTargetPointsHelper().getPointToNavigate();
	}

	public RoutingHelper getRoutingHelper() {
		return getApp().getRoutingHelper();
	}

	@Override
	protected void onPause() {
		super.onPause();
		getApp().getLocationProvider().pauseAllUpdates();
		getApp().getDaynightHelper().stopSensorIfNeeded();
		settings.APPLICATION_MODE.removeListener(applicationModeListener);

		settings.setLastKnownMapLocation((float) mapView.getLatitude(),
				(float) mapView.getLongitude());
		AnimateDraggingMapThread animatedThread = mapView
				.getAnimatedDraggingThread();
		if (animatedThread.isAnimating()
				&& animatedThread.getTargetIntZoom() != 0) {
			settings.setMapLocationToShow(animatedThread.getTargetLatitude(),
					animatedThread.getTargetLongitude(),
					animatedThread.getTargetIntZoom());
		}

		settings.setLastKnownMapZoom(mapView.getZoom());
		settings.MAP_ACTIVITY_ENABLED.set(false);
		getApp().getResourceManager().interruptRendering();
		getApp().getResourceManager().setBusyIndicator(null);
		OsmandPlugin.onMapActivityPause(this);
	}

	public void updateApplicationModeSettings() {
		// update vector renderer
		RendererRegistry registry = getApp().getRendererRegistry();
		RenderingRulesStorage newRenderer = registry
				.getRenderer(settings.RENDERER.get());
		if (newRenderer == null) {
			newRenderer = registry.defaultRender();
		}
		if (registry.getCurrentSelectedRenderer() != newRenderer) {
			registry.setCurrentSelectedRender(newRenderer);
			getApp().getResourceManager().getRenderer().clearCache();
		}
		mapViewTrackingUtilities.updateSettings();
		getApp().getRoutingHelper().setAppMode(settings.getApplicationMode());
		if (mapLayers.getMapInfoLayer() != null) {
			mapLayers.getMapInfoLayer().recreateControls();
		}
		mapLayers.updateLayers(mapView);
		mapView.setComplexZoom(mapView.getZoom(),
				mapView.getSettingsZoomScale());
		getApp().getDaynightHelper().startSensorIfNeeded(
				new StateChangedListener<Boolean>() {

					@Override
					public void stateChanged(Boolean change) {
						getMapView().refreshMap(true);
					}
				});
		getMapView().refreshMap(true);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
			if (!getApp().getInternalAPI().accessibilityEnabled()) {
				mapActions.contextMenuPoint(mapView.getLatitude(),
						mapView.getLongitude());
			} else if (uiHandler.hasMessages(LONG_KEYPRESS_MSG_ID)) {
				uiHandler.removeMessages(LONG_KEYPRESS_MSG_ID);
				mapActions.contextMenuPoint(mapView.getLatitude(),
						mapView.getLongitude());
			}
			return true;
		} else if (settings.ZOOM_BY_TRACKBALL.get()) {
			// Parrot device has only dpad left and right
			if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
				changeZoom(-1);
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
				changeZoom(1);
				return true;
			}
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
				|| keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
				|| keyCode == KeyEvent.KEYCODE_DPAD_DOWN
				|| keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			int dx = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 15
					: (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -15 : 0);
			int dy = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 15
					: (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -15 : 0);
			final RotatedTileBox tb = mapView.getCurrentRotatedTileBox();
			final QuadPoint cp = tb.getCenterPixelPoint();
			final LatLon l = tb.getLatLonFromPixel(cp.x + dx, cp.y + dy);
			setMapLocation(l.getLatitude(), l.getLongitude());
			return true;
		} else if (OsmandPlugin.onMapActivityKeyUp(this, keyCode)) {
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	public void checkExternalStorage() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// ok
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			AccessibleToast.makeText(this, R.string.sd_mounted_ro,
					Toast.LENGTH_LONG).show();
		} else {
			AccessibleToast.makeText(this, R.string.sd_unmounted,
					Toast.LENGTH_LONG).show();
		}
	}

	public void showAndHideMapPosition() {
		mapView.setShowMapPosition(true);
		getApp().runMessageInUIThreadAndCancelPrevious(SHOW_POSITION_MSG_ID,
				new Runnable() {
					@Override
					public void run() {
						if (mapView.isShowMapPosition()) {
							mapView.setShowMapPosition(false);
							mapView.refreshMap();
						}
					}
				}, 2500);
	}

	public OsmandMapTileView getMapView() {
		return mapView;
	}

	public MapViewTrackingUtilities getMapViewTrackingUtilities() {
		return mapViewTrackingUtilities;
	}

	protected void parseLaunchIntentLocation() {
		Intent intent = getIntent();
		if (intent != null && intent.getData() != null) {
			Uri data = intent.getData();
			if ("http".equalsIgnoreCase(data.getScheme())
					&& "download.osmand.net".equals(data.getHost())
					&& "/go".equals(data.getPath())) {
				String lat = data.getQueryParameter("lat");
				String lon = data.getQueryParameter("lon");
				if (lat != null && lon != null) {
					try {
						double lt = Double.parseDouble(lat);
						double ln = Double.parseDouble(lon);
						String zoom = data.getQueryParameter("z");
						int z = settings.getLastKnownMapZoom();
						if (zoom != null) {
							z = Integer.parseInt(zoom);
						}
						settings.setMapLocationToShow(lt, ln, z,
								getString(R.string.shared_location));
					} catch (NumberFormatException e) {
					}
				}
			}
		}
	}

	public MapActivityActions getMapActions() {
		return mapActions;
	}

	public MapActivityLayers getMapLayers() {
		return mapLayers;
	}

	public static void launchMapActivityMoveToTop(Context activity) {
		Intent newIntent = new Intent(activity, OsmandIntents.getMapActivity());
		newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		activity.startActivity(newIntent);
	}

	public static void launchMapActivityMoveToTopWithName(Context activity,
			String name) {
		Intent newIntent = new Intent(activity, OsmandIntents.getMapActivity());
		newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		// newIntent.putExtra(OsmandSettings.MAP_LABEL_TO_SHOW, name);
		newIntent.putExtra("name", name);
		newIntent.putExtra(SearchPOIActivity.POI_NAME, name);
		activity.startActivity(newIntent);
	}

	public static void launchMapActivityMoveToTopWithName(Context activity,
			String name, Location loc) {
		Intent newIntent = new Intent(activity, OsmandIntents.getMapActivity());
		newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		// newIntent.putExtra(OsmandSettings.MAP_LABEL_TO_SHOW, name);
		newIntent.putExtra("name", name);
		newIntent.putExtra(SearchPOIActivity.POI_NAME, name);
		activity.startActivity(newIntent);

		// new NavigateAction().getDirections(loc, name,null,
		// DirectionDialogStyle.create().routeToMapPoint());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		OsmandPlugin.onMapActivityResult(requestCode, resultCode, data);
	}

	public void refreshMap() {
		getMapView().refreshMap();
	}

	public OsmandApplication getApp() {
		return app;
	}

	public void setApp(OsmandApplication app) {
		this.app = app;
	}

}
