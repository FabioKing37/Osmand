package net.osmand.plus.activities;

import java.io.File;
import java.text.Collator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.actionbarsherlock.app.SherlockFragmentActivity;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.osmand.AndroidUtils;
import net.osmand.IndexConstants;
import net.osmand.Location;
import net.osmand.access.AccessibleAlertBuilder;
import net.osmand.access.AccessibleToast;
import net.osmand.data.Amenity;
import net.osmand.data.FavouritePoint;
import net.osmand.data.LatLon;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.map.ITileSource;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuAdapter.OnContextMenuClick;
import net.osmand.plus.FavouritesDbHelper;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.GPXUtilities.GPXFile;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.PoiFilter;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.activities.actions.NavigateAction;
import net.osmand.plus.activities.actions.NavigateAction.DirectionDialogStyle;
import net.osmand.plus.activities.actions.OsmAndDialogs;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.activities.search.SearchPOIActivity;
import net.osmand.plus.activities.search.SearchPoiFilterActivity;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.BaseMapLayer;
import net.osmand.plus.views.MapTileLayer;
import net.osmand.plus.views.OsmandMapTileView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MapActivityActions implements DialogProvider {

	public static final String KEY_LONGITUDE = "longitude";
	public static final String KEY_LATITUDE = "latitude";
	public static final String KEY_NAME = "name";
	public static final String KEY_FAVORITE = "favorite";
	public static final String KEY_ZOOM = "zoom";
	public String KEY_DESTNAME = "Destino";

	private static final int DIALOG_ADD_FAVORITE = 100;
	private static final int DIALOG_REPLACE_FAVORITE = 101;
	private static final int DIALOG_ADD_WAYPOINT = 102;
	private static final int DIALOG_RELOAD_TITLE = 103;

	private static final int DIALOG_SAVE_DIRECTIONS = 106;
	private static final int DIALOG_POI = 107;
	private static final int DIALOG_ROUTE = 108;
	// make static
	private static Bundle dialogBundle = new Bundle();

	private final MapActivity mapActivity;
	private OsmandSettings settings;
	private RoutingHelper routingHelper;

	public MapActivityActions(MapActivity mapActivity) {
		this.mapActivity = mapActivity;
		settings = mapActivity.getMyApplication().getSettings();
		routingHelper = mapActivity.getMyApplication().getRoutingHelper();
	}

	protected void addFavouritePoint(final double latitude,
			final double longitude) {
		String name = mapActivity.getMapLayers().getContextMenuLayer()
				.getSelectedObjectName();
		enhance(dialogBundle, latitude, longitude, name);
		mapActivity.showDialog(DIALOG_ADD_FAVORITE);
	}

	private Bundle enhance(Bundle aBundle, double latitude, double longitude,
			String name) {
		aBundle.putDouble(KEY_LATITUDE, latitude);
		aBundle.putDouble(KEY_LONGITUDE, longitude);
		aBundle.putString(KEY_NAME, name);
		return aBundle;
	}

	private Bundle enhance(Bundle bundle, double latitude, double longitude,
			final int zoom) {
		bundle.putDouble(KEY_LATITUDE, latitude);
		bundle.putDouble(KEY_LONGITUDE, longitude);
		bundle.putInt(KEY_ZOOM, zoom);
		return bundle;
	}

	public static void prepareAddFavouriteDialog(Activity activity,
			Dialog dialog, Bundle args, double lat, double lon, String name) {
		final Resources resources = activity.getResources();
		if (name == null) {
			name = resources
					.getString(R.string.add_favorite_dialog_default_favourite_name);
		}
		final FavouritePoint point = new FavouritePoint(lat, lon, name,
				resources.getString(R.string.favorite_default_category));
		args.putSerializable(KEY_FAVORITE, point);
		final EditText editText = (EditText) dialog.findViewById(R.id.Name);
		editText.setText(point.getName());
		editText.selectAll();
		editText.requestFocus();
		final AutoCompleteTextView cat = (AutoCompleteTextView) dialog
				.findViewById(R.id.Category);
		cat.setText(point.getCategory());
		AndroidUtils.softKeyboardDelayed(editText);
	}

	public static Dialog createAddFavouriteDialog(final Activity activity,
			final Bundle args) {
		Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.favourites_context_menu_edit);
		final View v = activity.getLayoutInflater().inflate(
				R.layout.favourite_edit_dialog, null, false);
		final FavouritesDbHelper helper = ((OsmandApplication) activity
				.getApplication()).getFavorites();
		builder.setView(v);
		final EditText editText = (EditText) v.findViewById(R.id.Name);
		final AutoCompleteTextView cat = (AutoCompleteTextView) v
				.findViewById(R.id.Category);
		cat.setAdapter(new ArrayAdapter<String>(activity,
				R.layout.list_textview, helper.getFavoriteGroups().keySet()
						.toArray(new String[] {})));

		builder.setNegativeButton(R.string.default_buttons_cancel, null);

		/*
		 * builder.setNeutralButton(R.string.update_existing, new
		 * DialogInterface.OnClickListener() {
		 * 
		 * @Override public void onClick(DialogInterface dialog, int which) { //
		 * Don't use showDialog because it is impossible to // refresh favorite
		 * items list Dialog dlg = createReplaceFavouriteDialog(activity, args);
		 * if (dlg != null) { dlg.show(); } //
		 * mapActivity.showDialog(DIALOG_REPLACE_FAVORITE); }
		 * 
		 * });
		 */
		builder.setPositiveButton(R.string.default_buttons_add,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						FavouritePoint point = (FavouritePoint) args
								.getSerializable(KEY_FAVORITE);
						final FavouritesDbHelper helper = ((OsmandApplication) activity
								.getApplication()).getFavorites();
						point.setName(editText.getText().toString().trim());
						point.setCategory(cat.getText().toString().trim());
						boolean added = helper.addFavourite(point);
						if (added) {
							AccessibleToast
									.makeText(
											activity,
											MessageFormat.format(
													activity.getString(R.string.add_favorite_dialog_favourite_added_template),
													point.getName()),
											Toast.LENGTH_SHORT).show();
						}
						if (activity instanceof MapActivity) {
							((MapActivity) activity).getMapView().refreshMap(
									true);
						}
					}
				});
		return builder.create();
	}

	protected static Dialog createReplaceFavouriteDialog(
			final Activity activity, final Bundle args) {
		final FavouritesDbHelper helper = ((OsmandApplication) activity
				.getApplication()).getFavorites();
		final List<FavouritePoint> points = new ArrayList<FavouritePoint>(
				helper.getFavouritePoints());
		final Collator ci = java.text.Collator.getInstance();
		Collections.sort(points, new Comparator<FavouritePoint>() {

			@Override
			public int compare(FavouritePoint object1, FavouritePoint object2) {
				return ci.compare(object1.getName(), object2.getName());
			}
		});
		final String[] names = new String[points.size()];
		if (names.length == 0) {
			AccessibleToast.makeText(activity,
					activity.getString(R.string.fav_points_not_exist),
					Toast.LENGTH_SHORT).show();
			return null;
		}

		Builder b = new AlertDialog.Builder(activity);
		final FavouritePoint[] favs = new FavouritePoint[points.size()];
		Iterator<FavouritePoint> it = points.iterator();
		int i = 0;
		while (it.hasNext()) {
			FavouritePoint fp = it.next();
			// filter gpx points
			if (fp.isStored()) {
				favs[i] = fp;
				names[i] = fp.getName();
				i++;
			}
		}
		b.setItems(names, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				FavouritePoint fv = favs[which];
				FavouritePoint point = (FavouritePoint) args
						.getSerializable(KEY_FAVORITE);
				if (helper.editFavourite(fv, point.getLatitude(),
						point.getLongitude())) {
					AccessibleToast.makeText(activity,
							activity.getString(R.string.fav_points_edited),
							Toast.LENGTH_SHORT).show();
				}
				if (activity instanceof MapActivity) {
					((MapActivity) activity).getMapView().refreshMap();
				}
			}
		});
		AlertDialog al = b.create();
		return al;
	}

	public void addWaypoint(final double latitude, final double longitude) {
		String name = mapActivity.getMapLayers().getContextMenuLayer()
				.getSelectedObjectName();
		enhance(dialogBundle, latitude, longitude, name);
		mapActivity.showDialog(DIALOG_ADD_WAYPOINT);
	}

	private Dialog createAddWaypointDialog(final Bundle args) {
		Builder builder = new AlertDialog.Builder(mapActivity);
		builder.setTitle(R.string.add_waypoint_dialog_title);
		FrameLayout parent = new FrameLayout(mapActivity);
		final EditText editText = new EditText(mapActivity);
		editText.setId(R.id.TextView);
		parent.setPadding(15, 0, 15, 0);
		parent.addView(editText);
		builder.setView(parent);
		builder.setNegativeButton(R.string.default_buttons_cancel, null);
		builder.setPositiveButton(R.string.default_buttons_add,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						double latitude = args.getDouble(KEY_LATITUDE);
						double longitude = args.getDouble(KEY_LONGITUDE);
						String name = editText.getText().toString();
						SavingTrackHelper savingTrackHelper = mapActivity
								.getMyApplication().getSavingTrackHelper();
						savingTrackHelper.insertPointData(latitude, longitude,
								System.currentTimeMillis(), name);
						if (settings.SHOW_CURRENT_GPX_TRACK.get()) {
							getMyApplication().getFavorites()
									.addFavoritePointToGPXFile(
											new FavouritePoint(latitude,
													longitude, name, ""));
						}
						AccessibleToast
								.makeText(
										mapActivity,
										MessageFormat
												.format(getString(R.string.add_waypoint_dialog_added),
														name),
										Toast.LENGTH_SHORT).show();
						dialog.dismiss();
					}
				});
		return builder.create();
	}

	public void reloadTile(final int zoom, final double latitude,
			final double longitude) {
		enhance(dialogBundle, latitude, longitude, zoom);
		mapActivity.showDialog(DIALOG_RELOAD_TITLE);
	}

	protected String getString(int res) {
		return mapActivity.getString(res);
	}

	protected void showToast(final String msg) {
		mapActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AccessibleToast.makeText(mapActivity, msg, Toast.LENGTH_LONG)
						.show();
			}
		});
	}

	protected void aboutRoute() {
		Intent intent = new Intent(mapActivity, ShowRouteInfoActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		mapActivity.startActivity(intent);
	}

	protected Location getLastKnownLocation() {
		return getMyApplication().getLocationProvider().getLastKnownLocation();
	}

	protected OsmandApplication getMyApplication() {
		return mapActivity.getMyApplication();
	}

	public void saveDirections() {
		mapActivity.showDialog(DIALOG_SAVE_DIRECTIONS);
	}

	public static Dialog createSaveDirections(Activity activity) {
		final OsmandApplication app = ((OsmandApplication) activity
				.getApplication());
		final File fileDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		final Dialog dlg = new Dialog(activity);
		dlg.setTitle(R.string.save_route_dialog_title);
		dlg.setContentView(R.layout.save_directions_dialog);
		final EditText edit = (EditText) dlg.findViewById(R.id.FileNameEdit);

		edit.setText("_"
				+ MessageFormat.format("{0,date,yyyy-MM-dd}", new Date()) + "_");
		((Button) dlg.findViewById(R.id.Save))
				.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String name = edit.getText().toString();
						fileDir.mkdirs();
						File toSave = fileDir;
						if (name.length() > 0) {
							if (!name.endsWith(".gpx")) {
								name += ".gpx";
							}
							toSave = new File(fileDir, name);
						}
						if (toSave.exists()) {
							dlg.findViewById(R.id.DuplicateFileName)
									.setVisibility(View.VISIBLE);
						} else {
							dlg.dismiss();
							new SaveDirectionsAsyncTask(app).execute(toSave);
						}
					}
				});

		((Button) dlg.findViewById(R.id.Cancel))
				.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						dlg.dismiss();
					}
				});

		return dlg;
	}

	private static class SaveDirectionsAsyncTask extends
			AsyncTask<File, Void, String> {

		private final OsmandApplication app;

		public SaveDirectionsAsyncTask(OsmandApplication app) {
			this.app = app;
		}

		@Override
		protected String doInBackground(File... params) {
			if (params.length > 0) {
				File file = params[0];
				GPXFile gpx = app.getRoutingHelper().generateGPXFileWithRoute();
				GPXUtilities.writeGpxFile(file, gpx, app);
				return app.getString(R.string.route_successfully_saved_at,
						file.getName());
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				AccessibleToast.makeText(app, result, Toast.LENGTH_LONG).show();
			}
		}

	}

	// TODO: ROTA
	public void contextMenuPoint(final double latitude, final double longitude,
			final ContextMenuAdapter iadapter, Object selectedObj,
			String selectedObjDesc, final String selectedObjName) {
		final ContextMenuAdapter adapter = iadapter == null ? new ContextMenuAdapter(
				mapActivity) : iadapter;

		adapter.item(R.string.context_menu_item_directions_to)
				.icons(R.drawable.ic_action_gdirections_dark,
						R.drawable.ic_action_gdirections_light).reg();
		final TargetPointsHelper targets = getMyApplication()
				.getTargetPointsHelper();

		/*
		 * if (targets.getPointToNavigate() != null) {
		 * adapter.item(R.string.context_menu_item_destination_point)
		 * .icons(R.drawable.ic_action_flag_dark,
		 * R.drawable.ic_action_flag_dark).reg();
		 * adapter.item(R.string.context_menu_item_intermediate_point)
		 * .icons(R.drawable.ic_action_flage_dark,
		 * R.drawable.ic_action_flage_light).reg(); // For button-less search UI
		 * } else { adapter.item(R.string.context_menu_item_destination_point)
		 * .icons(R.drawable.ic_action_flag_dark,
		 * R.drawable.ic_action_flag_light).reg(); }
		 * adapter.item(R.string.context_menu_item_directions_from)
		 * .icons(R.drawable.ic_action_gdirections_dark,
		 * R.drawable.ic_action_gdirections_light).reg();
		 * 
		 * adapter.item(R.string.context_menu_item_search)
		 * .icons(R.drawable.ic_action_search_dark,
		 * R.drawable.ic_action_search_light).reg();
		 * adapter.item(R.string.context_menu_item_share_location)
		 * .icons(R.drawable.ic_action_gshare_dark,
		 * R.drawable.ic_action_gshare_light).reg();
		 * adapter.item(R.string.context_menu_item_add_favorite)
		 * .icons(R.drawable.ic_action_fav_dark,
		 * R.drawable.ic_action_fav_light).reg();
		 */
		OsmandPlugin.registerMapContextMenu(mapActivity, latitude, longitude,
				adapter, selectedObj);
		// mapActivity.showDialog(DIALOG_POI);
		final Dialog builder = new Dialog(mapActivity);
		builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
		// builder.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		// final Builder builder = new AlertDialog.Builder(mapActivity);
		// Inflate and set the layout for the dialog
		// ListAdapter listAdapter;
		// LayoutInflater inflater = mapActivity.getLayoutInflater();

		// Pass null as the parent view because its going in the dialog layout
		// builder.setView(inflater.inflate(R.layout.list_menu_item_native2,
		// null));

		// TODO: If dialog with problems changed here the dialog

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			builder.setContentView(R.layout.list_menu_item_native2);
		} else {
			builder.setContentView(R.layout.list_menu_item_native2);
		}
		String name = selectedObjName;
		if (selectedObjName == null || selectedObjName.isEmpty()) {
			name = mapActivity.getMapLayers().getContextMenuLayer()
					.getSelectedObjectName();

		}
		String desc = selectedObjDesc;
		if (selectedObjDesc == null || selectedObjDesc.isEmpty()) {
			desc = mapActivity.getMapLayers().getContextMenuLayer()
					.getSelectedObjectDescription();
		}

		// if (selectedObj instanceof Amenity)

		// adding text dynamically
		if (selectedObj == null) {
			// IF ADDRESS
			TextView txtTop = (TextView) builder
					.findViewById(R.id.textViewAddress);
			txtTop.setText(R.string.address);

			TextView txt = (TextView) builder.findViewById(R.id.details);
			txt.setText(mapActivity.mapView.getContext().getString(
					R.string.point_on_map, latitude, longitude));
		} else {
			// IF POI
			TextView txtTop = (TextView) builder
					.findViewById(R.id.textViewAddress);
			if (name != null && !name.isEmpty())
				txtTop.setText(name);
			else {
				txtTop.setText(R.string.poi);
			}
			// String a = ((Amenity) selectedObj).getDescription();

			TextView txt = (TextView) builder.findViewById(R.id.details);
			txt.setMovementMethod(new ScrollingMovementMethod());
			txt.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			// txt.setGravity(0x01);
			txt.setText(mapActivity.mapView.getContext().getString(
					R.string.point_on_map_poi, desc));
		}

		// adding button click event
		ImageButton closeButton = (ImageButton) builder
				.findViewById(R.id.imageButtonClose);
		closeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				builder.dismiss();

			}
		});

		builder.show();

		Button poiButton = (Button) builder.findViewById(R.id.buttonPOI);
		poiButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				Intent intent = new Intent(mapActivity, OsmandIntents
						.getSearchActivity());
				// Intent intent2 = new Intent(mapActivity,
				// SearchPOIActivity.class);
				intent.putExtra(SearchActivity.SEARCH_LAT, latitude);
				intent.putExtra(SearchActivity.SEARCH_LON, longitude);
				intent.putExtra(SearchActivity.POI_CLOSE, true);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				builder.dismiss();

				/*
				 * LatLon loc = null; boolean searchAround = false;
				 * SherlockFragmentActivity parent = getSherlockActivity(); if
				 * (loc == null && parent instanceof SearchActivity) { loc =
				 * ((SearchActivity) parent).getSearchPoint(); searchAround =
				 * ((SearchActivity) parent) .isSearchAroundCurrentLocation(); }
				 * if (loc == null && !searchAround) { loc =
				 * mapActivity.getApp().getSettings().getLastKnownMapLocation();
				 * } if (loc != null && !searchAround) {
				 * intent2.putExtra(SearchActivity.SEARCH_LAT,
				 * loc.getLatitude());
				 * intent2.putExtra(SearchActivity.SEARCH_LON,
				 * loc.getLongitude()); }
				 */
				mapActivity.startActivity(intent);
			}
		});

		Button routeButton = (Button) builder.findViewById(R.id.buttonGo);
		routeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!routingHelper.isRouteBeingCalculated()
						&& !routingHelper.isRouteCalculated()) {
					Location loc = new Location("map");
					loc.setLatitude(latitude);
					loc.setLongitude(longitude);

					// String name =
					// mapActivity.getMapLayers().getContextMenuLayer().getSelectedObjectName();
					// String name = selectedObjName;
					String name = selectedObjName;
					// IF COORD
					if (selectedObjName == null || selectedObjName.isEmpty()) {
						name = mapActivity.getMapLayers().getContextMenuLayer()
								.getSelectedObjectName();
					}
					KEY_DESTNAME = name;
					builder.dismiss();
					new NavigateAction(mapActivity).getDirections(loc, name,
							null, DirectionDialogStyle.create()
									.routeToMapPoint());
					// .gpxRouteEnabled()
				} else {
					String name = selectedObjName;
					if (selectedObjName == null || selectedObjName.isEmpty()) {
						name = mapActivity.getMapLayers().getContextMenuLayer()
								.getSelectedObjectName();
					}
					KEY_DESTNAME = name;
					builder.dismiss();
					mapActivity.getApp().getRoutingHelper()
							.setFollowingMode(true);
					targets.navigateToPoint(new LatLon(latitude, longitude),
							true, -1, name);

				}
			}
		});

		Button favButton = (Button) builder.findViewById(R.id.buttonFav);
		favButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addFavouritePoint(latitude, longitude);

			}
		});

		/*
		 * if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
		 * listAdapter = adapter.createListAdapter(mapActivity,
		 * R.layout.list_menu_item, getMyApplication().getSettings()
		 * .isLightContentMenu()); } else { listAdapter =
		 * adapter.createListAdapter(mapActivity,
		 * R.layout.list_menu_item_native, getMyApplication()
		 * .getSettings().isLightContentMenu()); }
		 * 
		 * builder.setAdapter(listAdapter, new DialogInterface.OnClickListener()
		 * {
		 * 
		 * @Override public void onClick(DialogInterface dialog, int which) {
		 * int standardId = adapter.getItemId(which); OnContextMenuClick click =
		 * adapter.getClickAdapter(which); if (click != null) {
		 * click.onContextMenuClick(standardId, which, false, dialog); } else if
		 * (standardId == R.string.context_menu_item_search) { Intent intent =
		 * new Intent(mapActivity, OsmandIntents .getSearchActivity());
		 * intent.putExtra(SearchActivity.SEARCH_LAT, latitude);
		 * intent.putExtra(SearchActivity.SEARCH_LON, longitude);
		 * intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		 * mapActivity.startActivity(intent); } else if (standardId ==
		 * R.string.context_menu_item_directions_to) { if
		 * (!routingHelper.isRouteBeingCalculated() &&
		 * !routingHelper.isRouteCalculated()) { Location loc = new
		 * Location("map"); loc.setLatitude(latitude);
		 * loc.setLongitude(longitude); String name = mapActivity.getMapLayers()
		 * .getContextMenuLayer().getSelectedObjectName(); new
		 * NavigateAction(mapActivity).getDirections(loc, name,
		 * DirectionDialogStyle.create() .gpxRouteEnabled().routeToMapPoint());
		 * } else { String name = mapActivity.getMapLayers()
		 * .getContextMenuLayer().getSelectedObjectName();
		 * targets.navigateToPoint( new LatLon(latitude, longitude), true, -1,
		 * name); } } else if (standardId ==
		 * R.string.context_menu_item_directions_from) { if
		 * (targets.checkPointToNavigate(getMyApplication())) { Location loc =
		 * new Location("map"); loc.setLatitude(latitude);
		 * loc.setLongitude(longitude); String name = mapActivity.getMapLayers()
		 * .getContextMenuLayer().getSelectedObjectName(); new
		 * NavigateAction(mapActivity).getDirections(loc, name,
		 * DirectionDialogStyle.create()
		 * .gpxRouteEnabled().routeFromMapPoint()); } } else if (standardId ==
		 * R.string.context_menu_item_intermediate_point || standardId ==
		 * R.string.context_menu_item_destination_point) { boolean dest =
		 * standardId == R.string.context_menu_item_destination_point; String
		 * selected = mapActivity.getMapLayers()
		 * .getContextMenuLayer().getSelectedObjectName();
		 * targets.navigateToPoint(new LatLon(latitude, longitude), true, dest ?
		 * -1 : targets.getIntermediatePoints() .size(), selected); if
		 * (targets.getIntermediatePoints().size() > 0) {
		 * IntermediatePointsDialog .openIntermediatePointsDialog(mapActivity);
		 * } } else if (standardId == R.string.context_menu_item_share_location)
		 * { enhance(dialogBundle, latitude, longitude, mapActivity
		 * .getMapView().getZoom()); new ShareLocation(mapActivity).run(); }
		 * else if (standardId == R.string.context_menu_item_add_favorite) {
		 * addFavouritePoint(latitude, longitude); } } });
		 */
		// builder.create().show();

	}

	public void contextMenuPoint(final double latitude, final double longitude) {
		contextMenuPoint(latitude, longitude, null, null, null, null);
	}

	private Dialog createReloadTitleDialog(final Bundle args) {
		Builder builder = new AccessibleAlertBuilder(mapActivity);
		builder.setMessage(R.string.context_menu_item_update_map_confirm);
		builder.setNegativeButton(R.string.default_buttons_cancel, null);
		final OsmandMapTileView mapView = mapActivity.getMapView();
		builder.setPositiveButton(R.string.context_menu_item_update_map,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						int zoom = args.getInt(KEY_ZOOM);
						BaseMapLayer mainLayer = mapView.getMainLayer();
						if (!(mainLayer instanceof MapTileLayer)
								|| !((MapTileLayer) mainLayer).isVisible()) {
							AccessibleToast.makeText(mapActivity,
									R.string.maps_could_not_be_downloaded,
									Toast.LENGTH_SHORT).show();
							return;
						}
						final ITileSource mapSource = ((MapTileLayer) mainLayer)
								.getMap();
						if (mapSource == null
								|| !mapSource.couldBeDownloadedFromInternet()) {
							AccessibleToast.makeText(mapActivity,
									R.string.maps_could_not_be_downloaded,
									Toast.LENGTH_SHORT).show();
							return;
						}
						final RotatedTileBox tb = mapView
								.getCurrentRotatedTileBox();
						final QuadRect tilesRect = tb.getTileBounds();
						int left = (int) Math.floor(tilesRect.left);
						int top = (int) Math.floor(tilesRect.top);
						int width = (int) (Math.ceil(tilesRect.right) - left);
						int height = (int) (Math.ceil(tilesRect.bottom) - top);
						for (int i = 0; i < width; i++) {
							for (int j = 0; j < height; j++) {
								((OsmandApplication) mapActivity
										.getApplication()).getResourceManager()
										.clearTileImageForMap(null, mapSource,
												i + left, j + top, zoom);
							}
						}

						mapView.refreshMap();
					}
				});
		return builder.create();
	}

	@Override
	public Dialog onCreateDialog(int id) {
		Bundle args = dialogBundle;
		switch (id) {
		case DIALOG_ADD_FAVORITE:
			return createAddFavouriteDialog(mapActivity, args);
		case DIALOG_REPLACE_FAVORITE:
			return createReplaceFavouriteDialog(mapActivity, args);
		case DIALOG_ADD_WAYPOINT:
			return createAddWaypointDialog(args);
		case DIALOG_RELOAD_TITLE:
			return createReloadTitleDialog(args);
		case DIALOG_SAVE_DIRECTIONS:
			return createSaveDirections(mapActivity);
			/*
			 * case DIALOG_POI: return createPoiDialog(mapActivity); case
			 * DIALOG_POI: return createPoiDialog(mapActivity);
			 */

		}
		return OsmAndDialogs.createDialog(id, mapActivity, args);
	}

	@Override
	public void onPrepareDialog(int id, Dialog dialog) {
		Bundle args = dialogBundle;
		switch (id) {
		case DIALOG_ADD_FAVORITE:
			prepareAddFavouriteDialog(mapActivity, dialog, args,
					args.getDouble(KEY_LATITUDE),
					args.getDouble(KEY_LONGITUDE), args.getString(KEY_NAME));
			break;
		case DIALOG_ADD_WAYPOINT:
			EditText v = (EditText) dialog.getWindow().findViewById(
					R.id.TextView);
			v.setPadding(5, 0, 5, 0);
			if (args.getString(KEY_NAME) != null) {
				v.setText(args.getString(KEY_NAME));
			} else {
				v.setText("");
			}
			break;
		}
	}

	// Criar AlertDialog do Menu Princiapl atraves do XML
	public AlertDialog openOptionsMenuAsList() {
		final ContextMenuAdapter cm = createOptionsMenu();
		final Builder bld = new AlertDialog.Builder(mapActivity);
		bld.setTitle("  ");
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			bld.setIcon(R.drawable.logocompleto_white);
		} else {
			bld.setIcon(R.drawable.logocompleto);

		}
		bld.setNegativeButton(R.string.close,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});

		ListAdapter listAdapter;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			listAdapter = cm.createListAdapter(mapActivity,
					R.layout.list_menu_item, getMyApplication().getSettings()
							.isLightContentMenu());
		} else {
			listAdapter = cm.createListAdapter(mapActivity,
					R.layout.list_menu_item_native, getMyApplication()
							.getSettings().isLightContentMenu());
		}
		bld.setAdapter(listAdapter, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				OnContextMenuClick click = cm.getClickAdapter(which);
				if (click != null) {
					click.onContextMenuClick(cm.getItemId(which), which, false,
							dialog);
				}
			}
		});
		// Button close = bld.create().getButton(AlertDialog.BUTTON_NEGATIVE);
		// close.setBackgroundResource(R.drawable.map_btn_menu_o);

		return bld.show();

	}

	// TODO: Menu de Contexto Principal
	private ContextMenuAdapter createOptionsMenu() {
		final OsmandMapTileView mapView = mapActivity.getMapView();
		final OsmandApplication app = mapActivity.getMyApplication();
		ContextMenuAdapter optionsMenuHelper = new ContextMenuAdapter(app);

		// 1. Where am I
		/*
		 * optionsMenuHelper .item(R.string.where_am_i)
		 * .icons(R.drawable.ic_action_gloc_dark,
		 * R.drawable.ic_action_gloc_dark) .listen(new OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) { if
		 * (getMyApplication().getInternalAPI() .accessibilityEnabled()) {
		 * whereAmIDialog(); } else { mapActivity.getMapViewTrackingUtilities()
		 * .backToLocationImpl(); } } }).reg();
		 */
		// 2-4. Navigation related (directions, mute, cancel navigation)

		// ROUTE DIALOG
		boolean routeCalculated = routingHelper.getFinalLocation() != null
				|| routingHelper.isRouteCalculated();
		if (routeCalculated) {
			optionsMenuHelper
					.item(routingHelper.isRouteCalculated() ? R.string.get_directions
							: R.string.get_directions)
					.icons(R.drawable.ic_action_gdirections_dark,
							R.drawable.ic_action_gdirections_dark)
					.listen(new OnContextMenuClick() {
						@Override
						public void onContextMenuClick(int itemId, int pos,
								boolean isChecked, DialogInterface dialog) {
							if (routingHelper.isRouteCalculated()) {

								// Destino Atual
								Location loc = new Location("map");
								loc.setLatitude(routingHelper
										.getFinalLocation().getLatitude());
								loc.setLongitude(routingHelper
										.getFinalLocation().getLongitude());

								String name = KEY_DESTNAME;
								if (!(name != null)) {
									name = mapActivity.getMapLayers()
											.getContextMenuLayer()
											.getSelectedObjectName();
								}

								String routeInfo = routingHelper
										.getGeneralRouteInformationForDialog();
								new NavigateAction(mapActivity).getDirections(
										loc, name, routeInfo,
										DirectionDialogStyle.create()
												.routeToMapPoint());

								// aboutRoute();
							}
						}
					}).reg();
		}

		// Directions or ShowRoute
		/*
		 * if (!routingHelper.isRouteBeingCalculated() ||
		 * routingHelper.isRouteCalculated()) { optionsMenuHelper
		 * .item(routingHelper.isRouteCalculated() ? R.string.show_route :
		 * R.string.get_directions)
		 * .icons(R.drawable.ic_action_gdirections_dark,
		 * R.drawable.ic_action_gdirections_dark) .listen(new
		 * OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) { if
		 * (routingHelper.isRouteCalculated()) { aboutRoute(); } else { Location
		 * loc = new Location("map"); loc.setLatitude(mapView.getLatitude());
		 * loc.setLongitude(mapView.getLongitude());
		 * 
		 * new NavigateAction(mapActivity).getDirections( loc, null,
		 * DirectionDialogStyle .create().gpxRouteEnabled()); } } }).reg(); }
		 */
		// WayPoints
		/*
		 * if (getTargets().getPointToNavigate() != null) { optionsMenuHelper
		 * .item(R.string.target_points) .icons(R.drawable.ic_action_flage_dark,
		 * R.drawable.ic_action_flage_dark) .listen(new OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) { openIntermediatePointsDialog();
		 * } }).reg(); }
		 */

		// Pesquisa
		optionsMenuHelper
				.item(R.string.search_button)
				.icons(R.drawable.ic_action_search_dark,
						R.drawable.ic_action_search_dark)
				.listen(new OnContextMenuClick() {
					@Override
					public void onContextMenuClick(int itemId, int pos,
							boolean isChecked, DialogInterface dialog) {
						Intent newIntent = new Intent(mapActivity,
								OsmandIntents.getSearchActivity());
						// causes wrong position caching:
						// newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						LatLon loc = mapActivity.getMapLocation();
						newIntent.putExtra(SearchActivity.SEARCH_LAT,
								loc.getLatitude());
						newIntent.putExtra(SearchActivity.SEARCH_LON,
								loc.getLongitude());
						newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						mapActivity.startActivity(newIntent);
					}
				}).reg();
		// Favoritos
		optionsMenuHelper
				.item(R.string.favorites_Button)
				.icons(R.drawable.ic_action_fav_dark,
						R.drawable.ic_action_fav_dark)
				.listen(new OnContextMenuClick() {
					@Override
					public void onContextMenuClick(int itemId, int pos,
							boolean isChecked, DialogInterface dialog) {
						Intent newIntent = new Intent(mapActivity,
								OsmandIntents.getFavoritesActivity());
						// causes wrong position caching:
						// newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						mapActivity.startActivity(newIntent);
					}
				}).reg();

		// Histórico

		// 5-9. Default actions (Layers, Configure Map screen, Settings, Search,
		// Favorites)
		// COMMENTED : Layer que passou a POI...
		/*
		 * optionsMenuHelper .item(R.string.menu_layers)
		 * .icons(R.drawable.ic_action_layers_dark,
		 * R.drawable.ic_action_layers_dark) .listen(new OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) {
		 * mapActivity.getMapLayers().openLayerSelectionDialog( mapView); }
		 * }).reg();
		 */
		// POI filtro

		optionsMenuHelper
				.item(R.string.layer_poi)
				.selected(settings.SHOW_POI_OVER_MAP.get() ? 1 : 0)
				.icons(R.drawable.ic_action_info_dark,
						R.drawable.ic_action_info_dark)
				.listen(new OnContextMenuClick() {
					@Override
					public void onContextMenuClick(int itemId, int pos,
							boolean isChecked, DialogInterface dialog) {
						mapActivity.getMapLayers().openPOISelectionDialog(
								mapView);
					}
				}).reg();
		// Configurar Tela
	/*	optionsMenuHelper
				.item(R.string.layer_map_appearance)
				.icons(R.drawable.ic_action_settings_dark,
						R.drawable.ic_action_settings_dark)
				.listen(new OnContextMenuClick() {
					@Override
					public void onContextMenuClick(int itemId, int pos,
							boolean isChecked, DialogInterface dialog) {
						mapActivity.getMapLayers().getMapInfoLayer()
								.openViewConfigureDialog();
					}
				}).reg();
				*/
		// Settings
		optionsMenuHelper
				.item(R.string.settings_Button)
				.icons(R.drawable.ic_action_settings2_dark,
						R.drawable.ic_action_settings2_dark)
				.listen(new OnContextMenuClick() {
					@Override
					public void onContextMenuClick(int itemId, int pos,
							boolean isChecked, DialogInterface dialog) {
						final Intent intentSettings = new Intent(mapActivity,
								OsmandIntents.getSettingsActivity());
						mapActivity.startActivity(intentSettings);
					}
				}).reg();
		// Usar Localização - abre o menu de LongPress com a posição atual
		/*
		 * optionsMenuHelper .item(R.string.show_point_options)
		 * .icons(R.drawable.ic_action_marker_dark,
		 * R.drawable.ic_action_marker_dark) .listen(new OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) {
		 * contextMenuPoint(mapView.getLatitude(), mapView.getLongitude()); }
		 * }).reg();
		 */
		// ////////// Others
		// Sem estado de GPS, acedia a outras apps
		/*
		 * if (Version.isGpsStatusEnabled(app)) { optionsMenuHelper
		 * .item(R.string.show_gps_status)
		 * .icons(R.drawable.ic_action_gabout_dark,
		 * R.drawable.ic_action_gabout_dark) .listen(new OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) { new
		 * StartGPSStatus(mapActivity).run(); } }).reg(); }
		 */
		/*
		 * Sem dicas neste menu...Passa para as Definições optionsMenuHelper
		 * .item(R.string.tips_and_tricks)
		 * .icons(R.drawable.ic_action_ghelp_dark,
		 * R.drawable.ic_action_ghelp_dark) .listen(new OnContextMenuClick() {
		 * 
		 * @Override public void onContextMenuClick(int itemId, int pos, boolean
		 * isChecked, DialogInterface dialog) { TipsAndTricksActivity tactivity
		 * = new TipsAndTricksActivity( mapActivity); Dialog dlg =
		 * tactivity.getDialogToShowTips(false, true); dlg.show(); } }).reg();
		 */
		final OsmAndLocationProvider loc = app.getLocationProvider();
		// INICIAR ANIMAÇÂO - DEBUG
		/*
		if (app.getTargetPointsHelper().getPointToNavigate() != null
				|| loc.getLocationSimulation().isRouteAnimating()) {

			optionsMenuHelper
					.item(loc.getLocationSimulation().isRouteAnimating() ? R.string.animate_route_off
							: R.string.animate_route)
					.icons(R.drawable.ic_action_play_dark,
							R.drawable.ic_action_play_dark)
					.listen(new OnContextMenuClick() {

						@Override
						public void onContextMenuClick(int itemId, int pos,
								boolean isChecked, DialogInterface dialog) {
							// animate moving on route
							loc.getLocationSimulation()
									.startStopRouteAnimation(mapActivity);
						}
					}).reg();
		}
		*/
		OsmandPlugin.registerOptionsMenu(mapActivity, optionsMenuHelper);
		optionsMenuHelper
				.item(R.string.exit_Button)
				.icons(R.drawable.ic_action_quit_dark,
						R.drawable.ic_action_quit_dark)
				.listen(new OnContextMenuClick() {
					@Override
					public void onContextMenuClick(int itemId, int pos,
							boolean isChecked, DialogInterface dialog) {
						// 1. Work for almost all cases when user open apps from
						// main menu
						/*
						 * Intent newIntent = new Intent(mapActivity,
						 * OsmandIntents.getMainMenuActivity());
						 * newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						 * newIntent.putExtra(MainMenuActivity.APP_EXIT_KEY,
						 * MainMenuActivity.APP_EXIT_CODE);
						 * mapActivity.startActivity(newIntent);
						 */
						// In future when map will be main screen this should
						// change
						// MapActivity.launchMapActivityMoveToTop(mapActivity);
						app.closeApplication(mapActivity);
					}
				}).reg();
		return optionsMenuHelper;
	}

	public void openIntermediatePointsDialog() {
		IntermediatePointsDialog.openIntermediatePointsDialog(mapActivity);
	}

	public void stopNavigationAction(final OsmandMapTileView mapView) {
		if (routingHelper.isRouteCalculated()
				|| routingHelper.isFollowingMode()
				|| routingHelper.isRouteBeingCalculated()) {
			routingHelper.setFinalAndCurrentLocation(null,
					new ArrayList<LatLon>(), getLastKnownLocation(),
					routingHelper.getCurrentGPXRoute());
			// restore default mode
			settings.APPLICATION_MODE.set(settings.DEFAULT_APPLICATION_MODE
					.get());
			getTargets().clearPointToNavigate(true);
			mapView.refreshMap();
		} else {
			getTargets().clearPointToNavigate(true);
			mapView.refreshMap();
		}

	}

	private TargetPointsHelper getTargets() {
		return mapActivity.getMyApplication().getTargetPointsHelper();
	}

	public void stopNavigationActionConfirm(final OsmandMapTileView mapView) {
		Builder builder = new AlertDialog.Builder(mapActivity);

		if (routingHelper.isRouteCalculated()
				|| routingHelper.isFollowingMode()
				|| routingHelper.isRouteBeingCalculated()) {
			// Stop the navigation
			builder.setTitle(getString(R.string.cancel_route));
			builder.setMessage(getString(R.string.stop_routing_confirm));
			builder.setPositiveButton(R.string.default_buttons_yes,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (getMyApplication().getLocationProvider()
									.getLocationSimulation().isRouteAnimating()) {
								getMyApplication().getLocationProvider()
										.getLocationSimulation()
										.startStopRouteAnimation(mapActivity);
							}
							routingHelper.setFinalAndCurrentLocation(null,
									new ArrayList<LatLon>(),
									getLastKnownLocation(),
									routingHelper.getCurrentGPXRoute());
							settings.APPLICATION_MODE
									.set(settings.DEFAULT_APPLICATION_MODE
											.get());
							getTargets().clearPointToNavigate(true);
							mapView.refreshMap();
						}
					});
		} else {
			// Clear the destination point
			builder.setTitle(getString(R.string.cancel_navigation));
			builder.setMessage(getString(R.string.clear_dest_confirm));
			builder.setPositiveButton(R.string.default_buttons_yes,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							getTargets().clearPointToNavigate(true);
							mapView.refreshMap();
						}
					});
		}

		builder.setNegativeButton(R.string.default_buttons_no, null);
		builder.show();
	}

	public void stopNavigationActionConfirmMapnGo(
			final OsmandMapTileView mapView) {
		Builder builder = new AlertDialog.Builder(mapActivity);

		if (routingHelper.isRouteCalculated()
				|| routingHelper.isFollowingMode()
				|| routingHelper.isRouteBeingCalculated()) {
			// Stop the navigation
			builder.setTitle(getString(R.string.cancel_route));
			builder.setMessage(getString(R.string.stop_routing_confirm));
			builder.setPositiveButton(R.string.default_buttons_yes,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (getMyApplication().getLocationProvider()
									.getLocationSimulation().isRouteAnimating()) {
								getMyApplication().getLocationProvider()
										.getLocationSimulation()
										.startStopRouteAnimation(mapActivity);
							}
							routingHelper.setFinalAndCurrentLocation(null,
									new ArrayList<LatLon>(),
									getLastKnownLocation(),
									routingHelper.getCurrentGPXRoute());
							settings.APPLICATION_MODE
									.set(settings.DEFAULT_APPLICATION_MODE
											.get());
							getTargets().clearPointToNavigate(true);
							mapView.refreshMap();
						}
					});
		} else {
			// Clear the destination point
			builder.setTitle(getString(R.string.cancel_navigation));
			builder.setMessage(getString(R.string.clear_dest_confirm));
			builder.setPositiveButton(R.string.default_buttons_yes,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							getTargets().clearPointToNavigate(true);
							mapView.refreshMap();
						}
					});
		}

		builder.setNegativeButton(R.string.default_buttons_no, null);
		builder.show();
	}

	private void whereAmIDialog() {
		final List<String> items = new ArrayList<String>();
		items.add(getString(R.string.show_location));
		items.add(getString(R.string.show_details));
		AlertDialog.Builder menu = new AlertDialog.Builder(mapActivity);
		menu.setItems(items.toArray(new String[items.size()]),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int item) {
						dialog.dismiss();
						switch (item) {
						case 0:
							mapActivity.getMapViewTrackingUtilities()
									.backToLocationImpl();
							break;
						case 1:
							OsmAndLocationProvider locationProvider = getMyApplication()
									.getLocationProvider();
							locationProvider.showNavigationInfo(
									mapActivity.getPointToNavigate(),
									mapActivity);
							break;
						default:
							break;
						}
					}
				});
		menu.show();
	}

	public static void createDirectionsActions(final QuickAction qa,
			final LatLon location, final Object obj, final String name,
			final int z, final Activity activity, final boolean saveHistory,
			final OnClickListener onShow) {
		createDirectionsActions(qa, location, obj, name, z, activity,
				saveHistory, onShow, true);
	}

	// TODO : Create DirectionsActions
	public static void createDirectionsActions(final QuickAction qa,
			final LatLon location, final Object obj, final String name,
			final int z, final Activity activity, final boolean saveHistory,
			final OnClickListener onShow, boolean favorite) {

		final OsmandApplication app = ((OsmandApplication) activity
				.getApplication());
		final TargetPointsHelper targetPointsHelper = app
				.getTargetPointsHelper();

		ActionItem setAsDestination = new ActionItem();
		setAsDestination.setIcon(activity.getResources().getDrawable(
				R.drawable.ic_action_gdirections_blue));
		setAsDestination.setTitle(activity.getString(R.string.get_directions));
		setAsDestination.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onShow != null) {
					onShow.onClick(v);
				}

				qa.dismiss();
				String teste = name;
				app.getSettings().setMapLocationToShow(location.getLatitude(),
						location.getLongitude(), z, saveHistory ? name : null,
						name, obj);
				MapActivityActions.directionsToDialogAndLaunchMap(activity,
						location.getLatitude(), location.getLongitude(), name);
			}
		});
		qa.addActionItem(setAsDestination);

		// Set as Destination/Intermediate disable
		/*
		 * ActionItem intermediate = new ActionItem(); if
		 * (targetPointsHelper.getPointToNavigate() != null) {
		 * intermediate.setIcon(activity.getResources().getDrawable(
		 * R.drawable.ic_action_flage_light)); intermediate.setTitle(activity
		 * .getString(R.string.context_menu_item_intermediate_point)); } else {
		 * intermediate.setIcon(activity.getResources().getDrawable(
		 * R.drawable.ic_action_flag_light)); intermediate.setTitle(activity
		 * .getString(R.string.context_menu_item_destination_point)); }
		 * intermediate.setOnClickListener(new OnClickListener() {
		 * 
		 * @Override public void onClick(View v) { if (onShow != null) {
		 * onShow.onClick(v); } addWaypointDialogAndLaunchMap(activity,
		 * location.getLatitude(), location.getLongitude(), name); qa.dismiss();
		 * } }); qa.addActionItem(intermediate);
		 */
		ActionItem showOnMap = new ActionItem();
		showOnMap.setIcon(activity.getResources().getDrawable(
				R.drawable.ic_action_marker_blue));
		showOnMap.setTitle(activity.getString(R.string.show_poi_on_map));
		showOnMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onShow != null) {
					onShow.onClick(v);
				}
				app.getSettings().setMapLocationToShow(location.getLatitude(),
						location.getLongitude(), z, saveHistory ? name : null,
						name, obj); //$NON-NLS-1$
				MapActivity.launchMapActivityMoveToTop(activity);
				qa.dismiss();
			}
		});
		qa.addActionItem(showOnMap);

		if (favorite) {
			ActionItem addToFavorite = new ActionItem();
			addToFavorite.setIcon(activity.getResources().getDrawable(
					R.drawable.ic_action_fav_blue));
			addToFavorite.setTitle(activity
					.getString(R.string.add_to_favourite));
			addToFavorite.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onShow != null) {
						onShow.onClick(v);
					}
					qa.dismiss();
					Bundle args = new Bundle();
					Dialog dlg = createAddFavouriteDialog(activity, args);
					dlg.show();
					prepareAddFavouriteDialog(activity, dlg, args,
							location.getLatitude(), location.getLongitude(),
							name);

				}
			});
			qa.addActionItem(addToFavorite);
		}
	}

	public static void directionsToDialogAndLaunchMap(final Activity act,
			final double lat, final double lon, final String name) {
		final OsmandApplication ctx = (OsmandApplication) act.getApplication();
		final TargetPointsHelper targetPointsHelper = ctx
				.getTargetPointsHelper();
		if (targetPointsHelper.getIntermediatePoints().size() > 0) {
			Builder builder = new AlertDialog.Builder(act);
			builder.setTitle(R.string.new_directions_point_dialog);
			builder.setItems(
					new String[] {
							act.getString(R.string.keep_intermediate_points),
							act.getString(R.string.clear_intermediate_points) },
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (which == 1) {
								targetPointsHelper.clearPointToNavigate(false);
							}
							ctx.getSettings().navigateDialog();
							targetPointsHelper.navigateToPoint(new LatLon(lat,
									lon), true, -1, name);
							MapActivity.launchMapActivityMoveToTopWithName(act,
									name);
						}
					});
			builder.show();
		} else {
			if (targetPointsHelper.checkPointToNavigateMapnGo(ctx)) {
				targetPointsHelper.navigateToPoint(new LatLon(lat, lon), true,
						-1, name);
				MapActivity.launchMapActivityMoveToTop(act);
			} else {
				ctx.getSettings().navigateDialog();
				MapActivity.launchMapActivityMoveToTop(act);
			}
		}
	}

	public static void addWaypointDialogAndLaunchMap(final Activity act,
			final double lat, final double lon, final String name) {
		final OsmandApplication ctx = (OsmandApplication) act.getApplication();
		final TargetPointsHelper targetPointsHelper = ctx
				.getTargetPointsHelper();
		if (targetPointsHelper.getPointToNavigate() != null) {
			Builder builder = new AlertDialog.Builder(act);
			builder.setTitle(R.string.new_destination_point_dialog);
			builder.setItems(
					new String[] {
							act.getString(R.string.replace_destination_point),
							act.getString(R.string.add_as_first_destination_point),
							act.getString(R.string.add_as_last_destination_point) },
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (which == 0) {
								targetPointsHelper.navigateToPoint(new LatLon(
										lat, lon), true, -1, name);
							} else if (which == 2) {
								targetPointsHelper.navigateToPoint(new LatLon(
										lat, lon), true, targetPointsHelper
										.getIntermediatePoints().size(), name);
							} else {
								targetPointsHelper.navigateToPoint(new LatLon(
										lat, lon), true, 0, name);
							}
							MapActivity.launchMapActivityMoveToTop(act);
						}
					});
			builder.show();
		} else {
			targetPointsHelper.navigateToPoint(new LatLon(lat, lon), true, -1,
					name);
			MapActivity.launchMapActivityMoveToTop(act);
		}
	}

}
