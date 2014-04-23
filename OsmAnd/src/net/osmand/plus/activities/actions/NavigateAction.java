package net.osmand.plus.activities.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.osmand.CallbackWithObject;
import net.osmand.Location;
import net.osmand.access.AccessibleToast;
import net.osmand.data.LatLon;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.ContextMenuAdapter.OnContextMenuClick;
import net.osmand.plus.GPXUtilities.GPXFile;
import net.osmand.plus.OsmandSettings.OsmandPreference;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.OsmandIntents;
import net.osmand.plus.activities.ShowRouteInfoActivity;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.routing.RouteProvider.GPXRouteParams;
import net.osmand.plus.routing.RouteProvider.RouteService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.opengl.Visibility;
import android.os.Build;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class NavigateAction {

	private MapActivity mapActivity;
	private OsmandApplication app;
	private OsmandSettings settings;

	public NavigateAction(MapActivity mapActivity) {
		this.mapActivity = mapActivity;
		app = mapActivity.getMyApplication();
		settings = app.getSettings();
	}

	public void navigateUsingGPX(final ApplicationMode appMode) {
		final LatLon endForRouting = mapActivity.getPointToNavigate();
		mapActivity.getMapLayers().selectGPXFileLayer(false, false, false,
				new CallbackWithObject<GPXFile>() {

					@Override
					public boolean processResult(final GPXFile result) {
						return navigateUsingGPX(appMode, endForRouting, result);
					}
				});
	}

	public boolean navigateUsingGPX(final ApplicationMode appMode,
			final LatLon endForRouting, final GPXFile result) {
		Builder builder = new AlertDialog.Builder(mapActivity);
		final boolean[] props = new boolean[] { false, false, false,
				settings.SPEAK_GPX_WPT.get() };
		builder.setMultiChoiceItems(new String[] {
				getString(R.string.gpx_option_reverse_route),
				getString(R.string.gpx_option_destination_point),
				getString(R.string.gpx_option_from_start_point),
				getString(R.string.announce_gpx_waypoints) }, props,
				new OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which,
							boolean isChecked) {
						props[which] = isChecked;
					}
				});
		builder.setPositiveButton(R.string.default_buttons_apply,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						boolean reverse = props[0];
						boolean passWholeWay = props[2];
						boolean useDestination = props[1];
						boolean announceGpxWpt = props[3];
						settings.SPEAK_GPX_WPT.set(announceGpxWpt);
						GPXRouteParams gpxRoute = new GPXRouteParams(result,
								reverse, announceGpxWpt, settings);

						Location loc = getLastKnownLocation();
						if (passWholeWay && loc != null) {
							gpxRoute.setStartPoint(loc);
						}

						Location startForRouting = getLastKnownLocation();
						if (startForRouting == null) {
							startForRouting = gpxRoute.getStartPointForRoute();
						}

						LatLon endPoint = endForRouting;
						if (endPoint == null || !useDestination) {
							LatLon point = gpxRoute.getLastPoint();
							if (point != null) {
								endPoint = point;
							}
							if (endPoint != null) {
								app.getTargetPointsHelper().navigateToPoint(
										point, false, -1);
							}
						}
						if (endPoint != null) {
							mapActivity.followRoute(appMode, endPoint,
									new ArrayList<LatLon>(), startForRouting,
									gpxRoute);
							settings.FOLLOW_THE_GPX_ROUTE.set(result.path);
						}
					}

				});
		builder.setNegativeButton(R.string.default_buttons_cancel, null);
		builder.show();
		return true;
	}

	public void getDirections(final Location mapView, String name,
			String routeInfo, DirectionDialogStyle style) {
		final Location current = getLastKnownLocation();
		// Builder builder = new AlertDialog.Builder(mapActivity);

		final Dialog builderDialog = new Dialog(mapActivity);
		builderDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			builderDialog.setContentView(R.layout.calculate_route_mapngo);
		} else {
			builderDialog.setContentView(R.layout.calculate_route_mapngo);
		}

		final TargetPointsHelper targets = app.getTargetPointsHelper();
		

		final List<ApplicationMode> values = new ArrayList<ApplicationMode>(
				ApplicationMode.values(app.getSettings()));
		values.remove(ApplicationMode.DEFAULT);

		// final View view =
		// mapActivity.getLayoutInflater().inflate(R.layout.calculate_route,
		// null);

		// Fill Elements

		TextView destination = (TextView) builderDialog
				.findViewById(R.id.destination);

		if (targets.getPointToNavigate() != null) {
			if (routeInfo != null && !routeInfo.isEmpty()) {
				TextView routeDetails = (TextView) builderDialog
						.findViewById(R.id.routeDetails);
				routeDetails.setVisibility(View.VISIBLE);
				routeDetails.setText(routeInfo);
			}
		}

		if (mapView != null) {

			String destinationText = mapActivity
					.getString(R.string.route_descr_destination)
					+ " "
					+ getRoutePointDescription(targets.getPointToNavigate(),
							targets.getPointNavigateDescription());

			
				String oname = name != null ? name : getRoutePointDescription(
						mapView.getLatitude(), mapView.getLongitude());
			
			String mapLocation = mapActivity
					.getString(R.string.route_descr_map_location) + " " + oname;
			// mapActivity.getMapActions().KEY_DESTNAME = oname;
			String destRota = mapActivity.getMapLayers().getContextMenuLayer()
					.getSelectedObjectName();
			destination.setText(mapActivity.getMapView().getContext()
					.getString(R.string.destination_point, oname));
		}

		final CheckBox nonoptimal = (CheckBox) builderDialog
				.findViewById(R.id.OptimalCheckox);
		LinearLayout topLayout = (LinearLayout) builderDialog
				.findViewById(R.id.LinearLayout);
		final ToggleButton[] buttons = createToggles(values, topLayout,
				mapActivity);

		final Spinner fromSpinner = setupFromSpinner(mapView, name,
				builderDialog, style);
		final List<LatLon> toList = new ArrayList<LatLon>();
		final Spinner toSpinner = setupToSpinner(mapView, name, builderDialog,
				toList, style);
		// String item = toSpinner.getItemAtPosition(0).toString();

		String via = generateViaDescription();
		if (via.length() == 0) {
			((TextView) builderDialog.findViewById(R.id.ViaView))
					.setVisibility(View.GONE);
		} else {
			((TextView) builderDialog.findViewById(R.id.ViaView))
					.setVisibility(View.VISIBLE);
			((TextView) builderDialog.findViewById(R.id.ViaView)).setText(via);
		}

		ApplicationMode appMode = settings.getApplicationMode();
		if (appMode == ApplicationMode.DEFAULT) {
			appMode = ApplicationMode.CAR;
		}
		updateTooLongDistance(current != null ? current : mapView, targets,
				builderDialog, appMode);
		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i] != null) {
				final int ind = i;
				ToggleButton b = buttons[i];
				final ApplicationMode buttonAppMode = values.get(i);
				b.setChecked(appMode == buttonAppMode);
				if (b.isChecked()) {
					nonoptimal.setChecked(!settings.OPTIMAL_ROUTE_MODE
							.getModeValue(buttonAppMode));
				}
				b.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							nonoptimal.setChecked(!settings.OPTIMAL_ROUTE_MODE
									.getModeValue(buttonAppMode));
							updateTooLongDistance(current != null ? current
									: mapView, targets, builderDialog,
									buttonAppMode);
							for (int j = 0; j < buttons.length; j++) {
								if (buttons[j] != null) {
									if (buttons[j].isChecked() != (ind == j)) {
										buttons[j].setChecked(ind == j);
									}
								}
							}
						} else {
							// revert state
							boolean revert = true;
							for (int j = 0; j < buttons.length; j++) {
								if (buttons[j] != null) {
									if (buttons[j].isChecked()) {
										revert = false;
										break;
									}
								}
							}
							if (revert) {
								buttons[ind].setChecked(true);
							}
						}
					}
				});
			}
		}

		final ImageButton muteButton = (ImageButton) builderDialog
				.findViewById(R.id.ImageButtonMute);
		boolean muteVisible = mapActivity.getRoutingHelper().getFinalLocation() != null
				&& mapActivity.getRoutingHelper().isFollowingMode();
		// Mute
		if (muteVisible) {
			muteButton.setVisibility(View.VISIBLE);
			boolean mute = mapActivity.getRoutingHelper().getVoiceRouter()
					.isMute();

			int iconLight;
			if (mute) {

				iconLight = R.drawable.a_10_device_access_volume_muted_dark;
			} else {

				iconLight = R.drawable.a_10_device_access_volume_on_dark;
			}
			muteButton.setImageResource(iconLight);
			muteButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {

					// Button Mute
					mapActivity
							.getRoutingHelper()
							.getVoiceRouter()
							.setMute(
									!mapActivity.getRoutingHelper()
											.getVoiceRouter().isMute());
					boolean mute = mapActivity.getRoutingHelper()
							.getVoiceRouter().isMute();
					int iconLight;
					if (mute) {
						iconLight = R.drawable.a_10_device_access_volume_muted_dark;
					} else {

						iconLight = R.drawable.a_10_device_access_volume_on_dark;
					}
					muteButton.setImageResource(iconLight);

				}
			});

		}
		final ImageButton prefButton = (ImageButton) builderDialog
				.findViewById(R.id.ImageButtonPref);
		ApplicationMode mode = getAppMode(buttons, settings, values);
		if (mode != ApplicationMode.CAR) {
			// Disable options
			prefButton.setClickable(false);

		} else {
			prefButton.setClickable(true);

			final String[] vals = new String[] {
					getString(R.string.avoid_toll_roads),
					getString(R.string.avoid_ferries),
					getString(R.string.avoid_motorway) };
			@SuppressWarnings("unchecked")
			final OsmandPreference<Boolean>[] prefs = new OsmandPreference[] {
					settings.AVOID_TOLL_ROADS, settings.AVOID_FERRIES,
					settings.AVOID_MOTORWAY };

			final Builder bld = new AlertDialog.Builder(mapActivity);
			final boolean[] checkedItems = new boolean[prefs.length];
			for (int i = 0; i < prefs.length; i++) {
				checkedItems[i] = prefs[i].get();
			}
			prefButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {

					bld.setMultiChoiceItems(vals, checkedItems,
							new OnMultiChoiceClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which, boolean isChecked) {
									prefs[which].set(isChecked);
								}
							});
					bld.show();

				}
			});
			// Abrir alertDialog

		}

		// BUTTONS

		// adding button click event
		ImageButton closeButton = (ImageButton) builderDialog
				.findViewById(R.id.imageButtonClose);
		closeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				builderDialog.dismiss();

			}
		});

		Button goButton = (Button) builderDialog.findViewById(R.id.buttonGo);
		goButton.setVisibility(View.VISIBLE);
		goButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// LatLon tos1 =
				// toList.get(toSpinner.getSelectedItemPosition());
				LatLon tos = toList.get(0);
				if (tos != null && tos != targets.getPointToNavigate()) {
					targets.navigateToPoint(tos, false, -1);
				}
				if (!targets.checkPointToNavigate(app)) {
					return;
				}
				boolean msg = true;
				Location lastKnownLocation = getLastKnownLocation();
				Location from = fromSpinner.getSelectedItemPosition() == 0 ? current
						: mapView;
				if (from == null) {
					from = lastKnownLocation;
				}
				if (OsmAndLocationProvider
						.isPointAccurateForRouting(lastKnownLocation)) {
					from = lastKnownLocation;
					msg = false;
				}
				if (msg) {
					AccessibleToast
							.makeText(mapActivity,
									R.string.route_updated_loc_found,
									Toast.LENGTH_LONG).show();
				}
				ApplicationMode mode = getAppMode(buttons, settings, values);
				settings.OPTIMAL_ROUTE_MODE.setModeValue(mode,
						!nonoptimal.isChecked());
				builderDialog.dismiss();
				mapActivity.followRoute(mode, targets.getPointToNavigate(),
						targets.getIntermediatePoints(), from, null);
			}
		});

		// TODO: Para Rota
		if (mapActivity.getPointToNavigate() != null) {

			final Button stopRouteButton = (Button) builderDialog
					.findViewById(R.id.buttonStopRoute);
			stopRouteButton.setVisibility(View.VISIBLE);
			// goButton.setVisibility(View.GONE);
			stopRouteButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {

					mapActivity.getMapActions().stopNavigationActionConfirm(
							mapActivity.getMapView());
					stopRouteButton.setVisibility(View.GONE);
					builderDialog.dismiss();

				}
			});

		}

		Button previewButton = (Button) builderDialog
				.findViewById(R.id.buttonRoutePreview);
		previewButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// LatLon tos = toList.get(toSpinner.getSelectedItemPosition());
				LatLon tos = toList.get(0);
				if (tos != null && tos != targets.getPointToNavigate()) {
					targets.navigateToPoint(tos, false, -1);
				}
				if (!targets.checkPointToNavigate(app)) {
					return;
				}
				Location from = fromSpinner.getSelectedItemPosition() == 0 ? current
						: mapView;
				if (from == null) {
					from = getLastKnownLocation();
				}
				if (from == null) {

					AccessibleToast.makeText(mapActivity,
							R.string.unknown_from_location, Toast.LENGTH_LONG)
							.show();
					return;
				}

				ApplicationMode mode = getAppMode(buttons, settings, values);
				app.getRoutingHelper().setAppMode(mode);
				settings.OPTIMAL_ROUTE_MODE.setModeValue(mode,
						!nonoptimal.isChecked());
				settings.FOLLOW_THE_ROUTE.set(false);
				settings.FOLLOW_THE_GPX_ROUTE.set(null);
				app.getRoutingHelper().setFollowingMode(false);
				app.getRoutingHelper().setFinalAndCurrentLocation(
						targets.getPointToNavigate(),
						targets.getIntermediatePoints(), from, null);
				builderDialog.dismiss();
			}
		});

		Button listButton = (Button) builderDialog
				.findViewById(R.id.buttonRouteList);
		listButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// LatLon tos1 =
				// toList.get(toSpinner.getSelectedItemPosition());
				LatLon tos = toList.get(0);
				if (tos != null && tos != targets.getPointToNavigate()) {
					targets.navigateToPoint(tos, false, -1);
				}
				if (!targets.checkPointToNavigate(app)) {
					return;
				}
				boolean msg = true;
				Location lastKnownLocation = getLastKnownLocation();
				Location from = fromSpinner.getSelectedItemPosition() == 0 ? current
						: mapView;
				if (from == null) {
					from = lastKnownLocation;
				}
				if (OsmAndLocationProvider
						.isPointAccurateForRouting(lastKnownLocation)) {
					from = lastKnownLocation;
					msg = false;
				}
				if (msg) {
					AccessibleToast
							.makeText(mapActivity,
									R.string.route_updated_loc_found,
									Toast.LENGTH_LONG).show();
				}
				ApplicationMode mode = getAppMode(buttons, settings, values);
				settings.OPTIMAL_ROUTE_MODE.setModeValue(mode,
						!nonoptimal.isChecked());

				// mapActivity.followRoute(mode, targets.getPointToNavigate(),
				// targets.getIntermediatePoints(), from, null);
				Intent intent = new Intent(builderDialog.getContext(),
						ShowRouteInfoActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				builderDialog.getContext().startActivity(intent);
				builderDialog.dismiss();
			}
		});

		DialogInterface.OnClickListener onlyShowCall = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LatLon tos = toList.get(toSpinner.getSelectedItemPosition());
				if (tos != null && tos != targets.getPointToNavigate()) {
					targets.navigateToPoint(tos, false, -1);
				}
				if (!targets.checkPointToNavigate(app)) {
					return;
				}
				Location from = fromSpinner.getSelectedItemPosition() == 0 ? current
						: mapView;
				if (from == null) {
					from = getLastKnownLocation();
				}
				if (from == null) {
					AccessibleToast.makeText(mapActivity,
							R.string.unknown_from_location, Toast.LENGTH_LONG)
							.show();
					return;
				}

				ApplicationMode mode = getAppMode(buttons, settings, values);
				app.getRoutingHelper().setAppMode(mode);
				settings.OPTIMAL_ROUTE_MODE.setModeValue(mode,
						!nonoptimal.isChecked());
				settings.FOLLOW_THE_ROUTE.set(false);
				settings.FOLLOW_THE_GPX_ROUTE.set(null);
				app.getRoutingHelper().setFollowingMode(false);
				app.getRoutingHelper().setFinalAndCurrentLocation(
						targets.getPointToNavigate(),
						targets.getIntermediatePoints(), from, null);
			}
		};

		DialogInterface.OnClickListener followCall = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LatLon tos = toList.get(toSpinner.getSelectedItemPosition());
				if (tos != null && tos != targets.getPointToNavigate()) {
					targets.navigateToPoint(tos, false, -1);
				}
				if (!targets.checkPointToNavigate(app)) {
					return;
				}
				boolean msg = true;
				Location lastKnownLocation = getLastKnownLocation();
				Location from = fromSpinner.getSelectedItemPosition() == 0 ? current
						: mapView;
				if (from == null) {
					from = lastKnownLocation;
				}
				if (OsmAndLocationProvider
						.isPointAccurateForRouting(lastKnownLocation)) {
					from = lastKnownLocation;
					msg = false;
				}
				if (msg) {
					AccessibleToast
							.makeText(mapActivity,
									R.string.route_updated_loc_found,
									Toast.LENGTH_LONG).show();
				}
				ApplicationMode mode = getAppMode(buttons, settings, values);
				settings.OPTIMAL_ROUTE_MODE.setModeValue(mode,
						!nonoptimal.isChecked());
				dialog.dismiss();
				// mapActivity.followRoute(mode, targets.getPointToNavigate(),
				// targets.getIntermediatePoints(), from, null);
			}
		};

		DialogInterface.OnClickListener useGpxNavigation = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LatLon tos = toList.get(toSpinner.getSelectedItemPosition());
				if (tos != null && tos != targets.getPointToNavigate()) {
					targets.navigateToPoint(tos, false, -1);
				}
				ApplicationMode mode = getAppMode(buttons, settings, values);
				navigateUsingGPX(mode);
			}
		};

		/*
		 * builder.setView(view); builder.setTitle(R.string.get_directions);
		 * builder.setPositiveButton(R.string.follow, followCall);
		 * builder.setNeutralButton(R.string.only_show, onlyShowCall); //
		 * Deactivated if (style.gpxRouteEnabled) {
		 * builder.setNegativeButton(R.string.gpx_navigation, useGpxNavigation);
		 * } else { builder.setNegativeButton(R.string.no_route, null); }
		 * builder.show();
		 */
		builderDialog.show();
	}

	private void updateTooLongDistance(final Location start,
			final TargetPointsHelper targets, Dialog builderDialog,
			ApplicationMode appMode) {
		boolean osmandRouter = mapActivity.getMyApplication().getSettings().ROUTER_SERVICE
				.getModeValue(appMode) == RouteService.OSMAND;
		TextView textView = (TextView) builderDialog
				.findViewById(R.id.ValidateTextView);
		if (osmandRouter && targets.hasLongDistancesInBetween(start, 150000)) {
			textView.setText(R.string.route_is_too_long);
			textView.setVisibility(View.VISIBLE);
		} else {
			textView.setVisibility(View.GONE);
		}
	}

	private static ToggleButton[] createToggles(
			final List<ApplicationMode> values, LinearLayout topLayout,
			Context ctx) {
		final ToggleButton[] buttons = new ToggleButton[values.size()];
		HorizontalScrollView scroll = new HorizontalScrollView(ctx);

		topLayout.addView(scroll);
		LinearLayout ll = new LinearLayout(ctx);
		ll.setOrientation(LinearLayout.HORIZONTAL);
		scroll.addView(ll);

		int k = 0;
		int left = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				10, ctx.getResources().getDisplayMetrics());
		int metrics = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 64, ctx.getResources()
						.getDisplayMetrics());
		for (ApplicationMode ma : values) {
			ToggleButton tb = new ToggleButton(ctx);
			buttons[k++] = tb;
			tb.setTextOn("");
			tb.setTextOff("");
			tb.setContentDescription(ma.toHumanString(ctx));
			tb.setButtonDrawable(ma.getIconId());
			tb.setBackgroundResource(R.drawable.mapngo_btn_toggle_holo_light);
			LayoutParams lp = new LinearLayout.LayoutParams(metrics, metrics);
			lp.setMargins(left, 0, 0, 0);
			ll.addView(tb, lp);
		}
		return buttons;
	}

	public String getRoutePointDescription(double lat, double lon) {
		return mapActivity.getString(R.string.route_descr_lat_lon, lat, lon);
	}

	public String getRoutePointDescription(LatLon l, String d) {
		if (d != null && d.length() > 0) {
			return d.replace(':', ' ');
		}
		if (l != null) {
			return mapActivity.getString(R.string.route_descr_lat_lon,
					l.getLatitude(), l.getLongitude());
		}
		return "";
	}

	public String generateViaDescription() {
		TargetPointsHelper targets = getTargets();
		String via = "";
		List<String> names = targets.getIntermediatePointNames();
		List<LatLon> points = targets.getIntermediatePoints();
		if (names.size() == 0) {
			return via;
		}
		for (int i = 0; i < points.size(); i++) {
			via += "\n - "
					+ getRoutePointDescription(points.get(i),
							i >= names.size() ? "" : names.get(i));
		}
		return mapActivity.getString(R.string.route_via) + via;
	}

	public static class DirectionDialogStyle {
		public boolean gpxRouteEnabled;
		public boolean routeToMapPoint;
		public boolean routeFromMapPoint;

		public static DirectionDialogStyle create() {
			return new DirectionDialogStyle();
		}

		public DirectionDialogStyle gpxRouteEnabled() {
			gpxRouteEnabled = true;
			return this;
		}

		public DirectionDialogStyle routeToMapPoint() {
			routeToMapPoint = true;
			return this;
		}

		public DirectionDialogStyle routeFromMapPoint() {
			routeFromMapPoint = true;
			return this;
		}
	}

	public static View prepareAppModeView(Activity a,
			final Set<ApplicationMode> selected, boolean showDefault,
			ViewGroup parent, final boolean singleSelection,
			final View.OnClickListener onClickListener) {
		OsmandSettings settings = ((OsmandApplication) a.getApplication())
				.getSettings();
		final List<ApplicationMode> values = new ArrayList<ApplicationMode>(
				ApplicationMode.values(settings));
		if (!showDefault) {
			values.remove(ApplicationMode.DEFAULT);
		}
		selected.add(settings.getApplicationMode());
		return prepareAppModeView(a, values, selected, parent, singleSelection,
				onClickListener);

	}

	public static View prepareAppModeView(Activity a,
			final List<ApplicationMode> values,
			final Set<ApplicationMode> selected, ViewGroup parent,
			final boolean singleSelection,
			final View.OnClickListener onClickListener) {
		LinearLayout ll = (LinearLayout) a.getLayoutInflater().inflate(
				R.layout.mode_toggles, parent);
		final ToggleButton[] buttons = createToggles(values, ll, a);
		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i] != null) {
				final int ind = i;
				ToggleButton b = buttons[i];
				final ApplicationMode buttonAppMode = values.get(i);
				b.setChecked(selected.contains(buttonAppMode));
				b.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (singleSelection) {
							if (isChecked) {
								selected.clear();
								for (int j = 0; j < buttons.length; j++) {
									if (buttons[j] != null) {
										if (ind == j) {
											selected.add(values.get(j));
										}
										if (buttons[j].isChecked() != (ind == j)) {
											buttons[j].setChecked(ind == j);
										}
									}
								}
							} else {
								// revert state
								boolean revert = true;
								for (int j = 0; j < buttons.length; j++) {
									if (buttons[j] != null) {
										if (buttons[j].isChecked()) {
											revert = false;
											break;
										}
									}
								}
								if (revert) {
									buttons[ind].setChecked(true);
								}
							}
						} else {
							if (isChecked) {
								selected.add(buttonAppMode);
							} else {
								selected.remove(buttonAppMode);
							}
						}
						if (onClickListener != null) {
							onClickListener.onClick(null);
						}
					}
				});
			}
		}
		return ll;
	}

	private Spinner setupFromSpinner(final Location mapView, String name,
			Dialog view, DirectionDialogStyle style) {
		String currentLocation = mapActivity
				.getString(R.string.route_descr_current_location);
		ArrayList<String> fromActions = new ArrayList<String>();
		fromActions.add(currentLocation);
		if (mapView != null) {
			String oname = name != null ? name : getRoutePointDescription(
					mapView.getLatitude(), mapView.getLongitude());
			String mapLocation = mapActivity
					.getString(R.string.route_descr_map_location) + " " + oname;
			fromActions.add(mapLocation);
		}
		final Spinner fromSpinner = ((Spinner) view
				.findViewById(R.id.FromSpinner));
		ArrayAdapter<String> fromAdapter = new ArrayAdapter<String>(
				view.getContext(), android.R.layout.simple_spinner_item,
				fromActions);
		fromAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		fromSpinner.setAdapter(fromAdapter);
		if (style.routeFromMapPoint && mapView != null) {
			fromSpinner.setSelection(1);
		}
		return fromSpinner;
	}

	private Spinner setupToSpinner(final Location mapView, String name,
			Dialog view, List<LatLon> locs, DirectionDialogStyle style) {
		final TargetPointsHelper targets = getTargets();
		ArrayList<String> toActions = new ArrayList<String>();
		if (targets.getPointToNavigate() != null) {
			toActions.add(mapActivity
					.getString(R.string.route_descr_destination)
					+ " "
					+ getRoutePointDescription(targets.getPointToNavigate(),
							targets.getPointNavigateDescription()));
			locs.add(targets.getPointToNavigate());
		}
		if (mapView != null) {
			String oname = name != null ? name : getRoutePointDescription(
					mapView.getLatitude(), mapView.getLongitude());
			String mapLocation = mapActivity
					.getString(R.string.route_descr_map_location) + " " + oname;
			toActions.add(mapLocation);
			locs.add(new LatLon(mapView.getLatitude(), mapView.getLongitude()));
		}
		if (style.routeToMapPoint) {
			Collections.reverse(locs);
			Collections.reverse(toActions);
		}
		final Spinner toSpinner = ((Spinner) view.findViewById(R.id.ToSpinner));
		ArrayAdapter<String> toAdapter = new ArrayAdapter<String>(
				view.getContext(), android.R.layout.simple_spinner_item,
				toActions);
		toAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		toSpinner.setAdapter(toAdapter);
		return toSpinner;
	}

	private TargetPointsHelper getTargets() {
		return app.getTargetPointsHelper();
	}

	private Location getLastKnownLocation() {
		return app.getLocationProvider().getLastKnownLocation();
	}

	private String getString(int resId) {
		return mapActivity.getString(resId);
	}

	private static ApplicationMode getAppMode(ToggleButton[] buttons,
			OsmandSettings settings, List<ApplicationMode> modes) {
		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i] != null && buttons[i].isChecked()
					&& i < modes.size()) {
				return modes.get(i);
			}
		}
		return settings.getApplicationMode();
	}

}
