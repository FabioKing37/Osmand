package net.osmand.plus.views;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import net.osmand.data.RotatedTileBox;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.actions.NavigateAction;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.OsmandMapLayer.DrawSettings;
import net.osmand.plus.views.mapwidgets.AppearanceWidgetsFactory;
import net.osmand.plus.views.mapwidgets.BaseMapWidget;
import net.osmand.plus.views.mapwidgets.ImageViewWidget;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory;

import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopTextView;
import net.osmand.plus.views.mapwidgets.MapWidgetRegistry;
import net.osmand.plus.views.mapwidgets.MapWidgetRegistry.MapWidgetRegInfo;
import net.osmand.plus.views.mapwidgets.NextTurnInfoWidget;
import net.osmand.plus.views.mapwidgets.RouteInfoWidgetsFactory;
import net.osmand.plus.views.mapwidgets.StackWidgetView;
import net.osmand.plus.views.mapwidgets.TextInfoWidget;
import net.osmand.plus.views.mapwidgets.TurnStackWidgetView;
import net.osmand.plus.views.mapwidgets.UpdateableWidget;
import net.osmand.util.Algorithms;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;

public class MapInfoLayer extends OsmandMapLayer {

	public static float scaleCoefficient = 1;
	
	private final MapActivity map;
	private final RouteLayer routeLayer;
	private OsmandMapTileView view;
	
	private Paint paintText;
	private Paint paintSubText;
	private Paint paintSmallText;
	private Paint paintSmallSubText;
	private Paint paintImg;
	
	// layout pseudo-constants
	private int STATUS_BAR_MARGIN_X = -4;
	
	private ImageView backToLocationMeasures;
	private ImageView backToLocation;
	private TopTextView topText;
	private View progressBar;
	
	// groups
	private StackWidgetView rightStack;
	private StackWidgetView velStack;
	private TurnStackWidgetView leftStack;
	private LinearLayout statusBar;
	private LinearLayout infoBar;
	
	private BaseMapWidget lanesControl;
	private BaseMapWidget alarmControl;
	private MapWidgetRegistry mapInfoControls;

	private MonitoringInfoControl monitoringServices;

	public MapInfoLayer(MapActivity map, RouteLayer layer){
		this.map = map;
		this.routeLayer = layer;
	}
	
	
	public Paint getPaintSmallSubText() {
		return paintSmallSubText;
	}
	
	public Paint getPaintText() {
		return paintText;
	}
	
	public Paint getPaintSmallText() {
		return paintSmallText;
	}
	
	public Paint getPaintSubText() {
		return paintSubText;
	}
	
	public MonitoringInfoControl getMonitoringInfoControl() {
		return monitoringServices;
	}
	
	public MapWidgetRegistry getMapInfoControls() {
		return mapInfoControls;
	}

	@Override
	public void initLayer(final OsmandMapTileView view) {
		this.view = view;
		scaleCoefficient = view.getScaleCoefficient();

		//Cores do Texto na Widget
		paintText = new Paint();
		paintText.setStyle(Style.FILL_AND_STROKE);
		paintText.setColor(Color.WHITE);
		paintText.setTextSize(23 * scaleCoefficient);
		paintText.setAntiAlias(true);
		paintText.setStrokeWidth(4);

		paintSubText = new Paint();
		paintSubText.setStyle(Style.FILL_AND_STROKE);
		paintSubText.setColor(Color.BLACK);
		paintSubText.setTextSize(15 * scaleCoefficient);
		paintSubText.setAntiAlias(true);

		paintSmallText = new Paint();
		paintSmallText.setStyle(Style.FILL_AND_STROKE);
		paintSmallText.setColor(Color.BLACK);
		paintSmallText.setTextSize(19 * scaleCoefficient);
		paintSmallText.setAntiAlias(true);
		paintSmallText.setStrokeWidth(4);

		paintSmallSubText = new Paint();
		paintSmallSubText.setStyle(Style.FILL_AND_STROKE);
		paintSmallSubText.setColor(Color.WHITE);
		paintSmallSubText.setTextSize(13 * scaleCoefficient);
		paintSmallSubText.setAntiAlias(true);

		paintImg = new Paint();
		paintImg.setDither(true);
		paintImg.setFilterBitmap(true);
		paintImg.setAntiAlias(true);


		mapInfoControls = new MapWidgetRegistry(map.getMyApplication().getSettings());
		monitoringServices = new MonitoringInfoControl();


		registerAllControls();
		createControls();
	}
	
	public void registerAllControls(){
		statusBar = new LinearLayout(view.getContext());
		statusBar.setOrientation(LinearLayout.HORIZONTAL);
		
		infoBar = new LinearLayout(view.getContext());
		infoBar.setOrientation(LinearLayout.HORIZONTAL);
		
		RouteInfoWidgetsFactory ric = new RouteInfoWidgetsFactory(scaleCoefficient);
		MapInfoWidgetsFactory mic = new MapInfoWidgetsFactory(scaleCoefficient);
		OsmandApplication app = view.getApplication();
		lanesControl = ric.createLanesControl(app.getRoutingHelper(), view);
		lanesControl.setBackgroundDrawable(view.getResources().getDrawable(R.drawable.box_free));
		
		alarmControl = ric.createAlarmInfoControl(app, map);
		// register Left stack
		
		RoutingHelper routingHelper = app.getRoutingHelper();
		NextTurnInfoWidget bigInfoControl = ric.createNextInfoControl(routingHelper, app, view.getSettings(), paintText,
				paintSubText, false);
		mapInfoControls.registerSideWidget(bigInfoControl, R.drawable.widget_next_turn, R.string.map_widget_next_turn,"next_turn", true, 5);
		NextTurnInfoWidget smallInfoControl = ric.createNextInfoControl(routingHelper, app, view.getSettings(),
				paintSmallText, paintSmallSubText, true);
		mapInfoControls.registerSideWidget(smallInfoControl, R.drawable.widget_next_turn, R.string.map_widget_next_turn_small, "next_turn_small", true,
				10);
		NextTurnInfoWidget nextNextInfoControl = ric.createNextNextInfoControl(routingHelper, app, view.getSettings(),
				paintSmallText, paintSmallSubText, true);
		mapInfoControls.registerSideWidget(nextNextInfoControl, R.drawable.widget_next_turn, R.string.map_widget_next_next_turn, "next_next_turn",true, 15);
		//MiniMapControl miniMap = ric.createMiniMapControl(routingHelper, view);
		//mapInfoControls.registerSideWidget(miniMap, R.drawable.widget_next_turn, R.string.map_widget_mini_route, "mini_route", true, none, none, 20);
		
		// right stack
		TextInfoWidget intermediateDist = ric.createIntermediateDistanceControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(intermediateDist, R.drawable.widget_intermediate, R.string.map_widget_intermediate_distance, "intermediate_distance", false, 3);
		TextInfoWidget dist = ric.createDistanceControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(dist, R.drawable.widget_target, R.string.map_widget_distance, "distance", false, 5);
		TextInfoWidget time = ric.createTimeControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(time, R.drawable.widget_time, R.string.map_widget_time, "time",false, 10);
		TextInfoWidget speed = ric.createSpeedControl(map, paintText, paintSubText);
		mapInfoControls.registerVelWidget(speed, R.drawable.widget_speed, R.string.map_widget_speed, "speed", 15);
		TextInfoWidget gpsInfo = mic.createGPSInfoControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(gpsInfo, R.drawable.widget_gps_info, R.string.map_widget_gps_info, "gps_info", false, 17);
		TextInfoWidget maxspeed = ric.createMaxSpeedControl(map, paintText, paintSubText);
		mapInfoControls.registerVelWidget(maxspeed, R.drawable.widget_max_speed, R.string.map_widget_max_speed, "max_speed",  18);
		TextInfoWidget alt = mic.createAltitudeControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(alt, R.drawable.widget_altitude, R.string.map_widget_altitude, "altitude", false, 20);
		
		// InfoBar
		/*TextInfoWidget intermediateDist2 = ric.createIntermediateDistanceControl(map, paintText, paintSubText);
		mapInfoControls.registerInfoWidget(intermediateDist2, R.drawable.widget_intermediate, R.string.map_widget_intermediate_distance, "intermediate_distance", MapWidgetRegistry.LEFT_CONTROL, 3);
		TextInfoWidget dist2 = ric.createDistanceControl(map, paintText, paintSubText);
		mapInfoControls.registerInfoWidget(dist2, R.drawable.widget_target, R.string.map_widget_distance, "distance", MapWidgetRegistry.RIGHT_CONTROL, 5);
		TextInfoWidget time = ric.createTimeControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(time, R.drawable.widget_time, R.string.map_widget_time, "time",false, 10);
		TextInfoWidget speed = ric.createSpeedControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(speed, R.drawable.widget_speed, R.string.map_widget_speed, "speed", false, 15);
		TextInfoWidget gpsInfo = mic.createGPSInfoControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(gpsInfo, R.drawable.widget_gps_info, R.string.map_widget_gps_info, "gps_info", false, 17);
		TextInfoWidget maxspeed = ric.createMaxSpeedControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(maxspeed, R.drawable.widget_max_speed, R.string.map_widget_max_speed, "max_speed", false,  18);
		TextInfoWidget alt = mic.createAltitudeControl(map, paintText, paintSubText);
		mapInfoControls.registerSideWidget(alt, R.drawable.widget_altitude, R.string.map_widget_altitude, "altitude", false, 20);
*/
		// Top widgets
		ImageViewWidget compassView = mic.createCompassView(map);
		mapInfoControls.registerTopWidget(compassView, R.drawable.widget_compass, R.string.map_widget_compass, "compass", MapWidgetRegistry.LEFT_CONTROL, 5);
		
		View config = createConfiguration();
		mapInfoControls.registerTopWidget(config, R.drawable.widget_config, R.string.map_widget_config, "config", MapWidgetRegistry.RIGHT_CONTROL, 10);
		mapInfoControls.registerTopWidget(monitoringServices.createMonitoringWidget(view, map), R.drawable.widget_monitoring, R.string.map_widget_monitoring_services,
				"monitoring_services", MapWidgetRegistry.LEFT_CONTROL, 12);
		mapInfoControls.registerTopWidget(mic.createLockInfo(map), R.drawable.widget_lock_screen, R.string.bg_service_screen_lock, "bgService", 
				MapWidgetRegistry.LEFT_CONTROL,  15);
		//View whereIam = mic.createBackToLocationWithProgress(map,getProgressBar());
		View backToLocation1 = createBackToLocationWithProgress(map);
		backToLocationMeasures = mic.createBackToLocation(map);

		mapInfoControls.registerTopWidget(backToLocation1, R.drawable.widget_backtolocation, R.string.map_widget_back_to_loc, "back_to_location", MapWidgetRegistry.RIGHT_CONTROL, 5);
		
		View globus = createLayer();
		mapInfoControls.registerTopWidget(globus, R.drawable.widget_layer, R.string.menu_layers, "layers", MapWidgetRegistry.RIGHT_CONTROL, 15);
		
		topText = mic.createStreetView(app, map, paintText);
		mapInfoControls.registerTopWidget(topText, R.drawable.street_name, R.string.map_widget_top_text,
				"street_name", MapWidgetRegistry.MAIN_CONTROL, 100);
		
		// Register appearance widgets
		AppearanceWidgetsFactory.INSTANCE.registerAppearanceWidgets(map, this, mapInfoControls);
	}
	
	

	public void recreateControls(){
		rightStack.clearAllViews();
		mapInfoControls.populateStackControl(rightStack, view, false);
		
		velStack.clearAllViews();
		mapInfoControls.populateVelStackControl(velStack, view, true);
		
		leftStack.clearAllViews();
		mapInfoControls.populateTurnStackControl(leftStack, view, true);
		leftStack.requestLayout();
		rightStack.requestLayout();
		velStack.requestLayout();
		
		statusBar.removeAllViews();
		mapInfoControls.populateStatusBar(statusBar);
		
		//infoBar.removeAllViews();
		//mapInfoControls.populateInfoBar(infoBar);
		
		updateColorShadowsOfText(null);
	}

	public void createControls() {
		// 1. Create view groups and controls
		statusBar.setBackgroundDrawable(view.getResources().getDrawable(R.drawable.box_top));
		//infoBar.setBackgroundDrawable(view.getResources().getDrawable(R.drawable.box_top));
		rightStack = new StackWidgetView(view.getContext());
		velStack = new StackWidgetView(view.getContext());
		leftStack = new TurnStackWidgetView(view.getContext());
		
		// 2. Preparations
		Rect topRectPadding = new Rect();
		view.getResources().getDrawable(R.drawable.box_top).getPadding(topRectPadding);
		// for measurement
		statusBar.addView(backToLocationMeasures);	
		//infoBar.addView();
		STATUS_BAR_MARGIN_X = (int) (STATUS_BAR_MARGIN_X * scaleCoefficient);
		statusBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
		//infoBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
		Rect statusBarPadding = new Rect();
		statusBar.getBackground().getPadding(statusBarPadding);
		//infoBar.getBackground().getPadding(statusBarPadding);
		// 3. put into frame parent layout controls
		FrameLayout parent = (FrameLayout) view.getParent();
		// status bar hides own top part 
		int topMargin = statusBar.getMeasuredHeight()  - statusBarPadding.top - statusBarPadding.bottom ;
		// we want that status bar lays over map stack controls
		topMargin -= topRectPadding.top;

		//RightStack
		
		FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
		flp.bottomMargin = STATUS_BAR_MARGIN_X;
		//flp.leftMargin = (int) (scaleCoefficient * 4);
		flp.leftMargin = STATUS_BAR_MARGIN_X;
		rightStack.setLayoutParams(flp);
		
		//VelStack

		flp = new FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
		flp.bottomMargin = STATUS_BAR_MARGIN_X;
		flp.rightMargin = STATUS_BAR_MARGIN_X;
		velStack.setLayoutParams(flp);

		
		//Lanes - Vias
		flp = new FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
		flp.topMargin = (int) (topMargin  + scaleCoefficient * 6);
		lanesControl.setLayoutParams(flp);
		
		//Left - Direções
		flp = new FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				
				android.view.ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
		flp.leftMargin = STATUS_BAR_MARGIN_X;
		flp.topMargin = topMargin + STATUS_BAR_MARGIN_X;	
//		flp.bottomMargin = -topMargin + STATUS_BAR_MARGIN_X;
		leftStack.setLayoutParams(flp);

		
		//StatusBAr
		flp = new FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
		flp.leftMargin = STATUS_BAR_MARGIN_X;
		flp.rightMargin = STATUS_BAR_MARGIN_X;
		flp.topMargin = -topRectPadding.top;
		statusBar.setLayoutParams(flp);
		
		//InfoBar
		/*flp = new FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
		flp.leftMargin = STATUS_BAR_MARGIN_X;
		flp.rightMargin = STATUS_BAR_MARGIN_X;
		//flp.topMargin = (int) (85*scaleCoefficient);
		//flp.topMargin = - (2*topRectPadding.top);
		flp.bottomMargin = 0;
		infoBar.setLayoutParams(flp);*/
		
		flp = new FrameLayout.LayoutParams((int)(78 * scaleCoefficient),
				(int)(78 * scaleCoefficient), Gravity.RIGHT | Gravity.BOTTOM);
		flp.rightMargin = (int) (10*scaleCoefficient);
		flp.bottomMargin = (int) (85*scaleCoefficient);
		alarmControl.setLayoutParams(flp);

		//TODO: Ver este addView
		parent.addView(rightStack);
		parent.addView(velStack);		
		parent.addView(leftStack);
		parent.addView(statusBar);
		//parent.addView(infoBar);
		parent.addView(lanesControl);
		parent.addView(alarmControl);
		alarmControl.setVisibility(View.GONE);
		lanesControl.setVisibility(View.GONE);
		
		// update and create controls
		recreateControls();
	}
	
	public Set<String> getSpecificVisibleCategories(Set<MapWidgetRegInfo> m) {
		Set<String> s = new LinkedHashSet<String>();
		for(MapWidgetRegInfo ms : m){
			if(ms.getCategory() != null) {
				s.add(ms.getCategory());
			}
		}
		return s;
	}
	
	public void fillAppearanceWidgets(Set<MapWidgetRegInfo> widgets, String category, ArrayList<Object> registry) {
		for(MapWidgetRegInfo w : widgets ) {
			if(Algorithms.objectEquals(w.getCategory(), category)) {
				registry.add(w);
			}
		}
	}

	//TODO: Configurar Tela Dialog
	public void openViewConfigureDialog() {
		final OsmandSettings settings = view.getSettings();
		
		final ArrayList<Object> list = new ArrayList<Object>();
		list.add(map.getString(R.string.map_widget_reset));
		list.add(map.getString(R.string.map_widget_top_stack));
		list.addAll(mapInfoControls.getTop());
		list.add(map.getString(R.string.map_widget_right_stack));
		list.addAll(mapInfoControls.getRight());
		list.add(map.getString(R.string.map_widget_left_stack));
		list.addAll(mapInfoControls.getLeft());

		Set<MapWidgetRegInfo> widgets = mapInfoControls.getAppearanceWidgets();
		Set<String> cats = getSpecificVisibleCategories(widgets);
		list.add(map.getString(R.string.map_widget_appearance));
		fillAppearanceWidgets(widgets, null, list);
		for(String cat : cats) {
			list.add(cat);
			fillAppearanceWidgets(widgets, cat, list);
		}
		

		// final LayerMenuListener listener = new LayerMenuListener(adapter, mapView, settings);
		final Set<ApplicationMode> selected = new LinkedHashSet<ApplicationMode>();
		final ArrayAdapter<Object> listAdapter = new ArrayAdapter<Object>(map, R.layout.layers_list_activity_item, R.id.title, list) {
			@Override
			public View getView(final int position, View convertView, ViewGroup parent) {
				final ApplicationMode mode = settings.getApplicationMode();
				View v = convertView;
				if (v == null) {
					v = map.getLayoutInflater().inflate(R.layout.layers_list_activity_item, null);
				}
				final TextView tv = (TextView) v.findViewById(R.id.title);
				final CheckBox ch = ((CheckBox) v.findViewById(R.id.check_item));
				Object o = list.get(position);
				if(o instanceof MapWidgetRegInfo) {
					final MapWidgetRegInfo mi = (MapWidgetRegInfo) o;
					
					String s = mi.visibleCollapsed(mode)? " - " : "  ";
					if(mi.message != null) {
						tv.setText(s +mi.message +s);	
					} else {
						tv.setText(s +map.getString(mi.messageId) +s);
					}
					// Put the image on the TextView
					if (mi.drawable != 0) {
						tv.setPadding((int) (12 *scaleCoefficient), 0, 0, 0);
						tv.setCompoundDrawablesWithIntrinsicBounds(mi.drawable, 0, 0, 0);
					} else {
						tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
						tv.setPadding((int) (30 *scaleCoefficient), 0, 0, 0);
					}
					final boolean selecteable = mi.selecteable();
					ch.setOnCheckedChangeListener(null);
					if(!mi.selecteable()) {
						ch.setVisibility(View.GONE);
					} else {
						boolean check = mi.visibleCollapsed(mode) || mi.visible(mode);
						ch.setChecked(check);
						ch.setVisibility(View.VISIBLE);
					}
					ch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							mapInfoControls.changeVisibility(mi);
							if (selecteable) {
								ch.setChecked(mi.visible(mode) || mi.visibleCollapsed(mode));
							}
							String s = mi.visibleCollapsed(mode) ? " - " : "  ";
							if(mi.message != null) {
								tv.setText(s +mi.message +s);	
							} else {
								tv.setText(s +map.getString(mi.messageId) +s);
							}
							recreateControls();
						}
					});
				} else {
					tv.setText(o.toString());
					tv.setPadding((int) (5 *scaleCoefficient), 0, 0, 0);
					// reset 
					if (position == 0) {
						tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.widget_reset_to_default, 0, 0, 0);
					} else {
						tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
					}
					ch.setVisibility(View.INVISIBLE);
				}
				
				return v;
			}
		};
		Builder b = new AlertDialog.Builder(map);
		View confirmDialog = view.inflate(view.getContext(), R.layout.configuration_dialog, null);
		final ListView lv = (ListView) confirmDialog.findViewById(android.R.id.list);
		NavigateAction.prepareAppModeView(map, selected, true, 
				(ViewGroup) confirmDialog.findViewById(R.id.TopBar), false, 
				new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(selected.size() > 0) {
					view.getSettings().APPLICATION_MODE.set(selected.iterator().next());
					listAdapter.notifyDataSetChanged();
				}
			}
		});
		
		
		lv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final ApplicationMode mode = settings.getApplicationMode();
				Object o = list.get(position);
				if (o instanceof MapWidgetRegInfo) {
					final MapWidgetRegInfo mi = (MapWidgetRegInfo) o;
					final boolean selecteable = mi.selecteable();
					boolean check = mi.visibleCollapsed(mode) || mi.visible(mode);
					if (check || selecteable) {
						mapInfoControls.changeVisibility(mi);
					}
					recreateControls();
					listAdapter.notifyDataSetInvalidated();
				} else if(position == 0) {
					mapInfoControls.resetToDefault();
					recreateControls();
					listAdapter.notifyDataSetInvalidated();
				}				
			}
		});
		lv.setAdapter(listAdapter);
		b.setView(confirmDialog);
		final AlertDialog dlg = b.create();
		// listener.setDialog(dlg);
		dlg.setCanceledOnTouchOutside(true);
		dlg.show();
	}
	
	private int themeId = -1;
	public void updateColorShadowsOfText(DrawSettings drawSettings) {
		boolean transparent = view.getSettings().TRANSPARENT_MAP_THEME.get();
		boolean nightMode = drawSettings == null ? false : drawSettings.isNightMode();
		boolean following = routeLayer.getHelper().isFollowingMode();
		int calcThemeId = (transparent ? 4 : 0) | (nightMode ? 2 : 0) | (following ? 1 : 0);
		if (themeId != calcThemeId) {
			themeId = calcThemeId;
			boolean textBold = following;
			int textColor = nightMode ? Color.WHITE:Color.WHITE ;
			int textShadowColor = transparent && !nightMode? Color.BLACK : Color.DKGRAY ;
			int boxTop;
			int boxBottom;
			int boxTopStack;
			int boxTopR;
			int boxTopL;
			int expand;
			Drawable boxFree = view.getResources().getDrawable(R.drawable.box_free_simple);
			
			if (transparent) {
				boxBottom = R.drawable.box_top_t;
				boxTop = R.drawable.box_top_t;
				boxTopStack = R.drawable.box_top_t_stack;
				boxTopR = R.drawable.box_top_rt;
				boxTopL = R.drawable.box_top_lt;
				expand = R.drawable.box_expand_t;
				if (nightMode) {
					boxFree = view.getResources().getDrawable(R.drawable.box_night_free_simple);
				}
			} else if (nightMode) {
				boxBottom = R.drawable.box_top_t;
				boxTop = R.drawable.box_top;
				boxTopStack = R.drawable.box_top_stack;
				boxTopR = R.drawable.box_top_r;
				boxTopL = R.drawable.box_top_l;
				expand = R.drawable.box_expand;
				boxFree = view.getResources().getDrawable(R.drawable.box_free_simple);
			} else {
				//Res das boxes
				boxBottom = R.drawable.box_top_t;
				boxTop = R.drawable.box_top;
				boxTopStack = R.drawable.box_top_stack;
				boxTopR = R.drawable.box_top_r;
				boxTopL = R.drawable.box_top_l;
				expand = R.drawable.box_expand;
			}
			lanesControl.setBackgroundDrawable(boxFree);
			rightStack.setTopDrawable(view.getResources().getDrawable(boxTopR));
			rightStack.setStackDrawable(boxTopR);
			
			velStack.setTopDrawable(view.getResources().getDrawable(boxTopR));
			velStack.setStackDrawable(boxTopR);

			leftStack.setTopDrawable(view.getResources().getDrawable(boxTopL));
			leftStack.setStackDrawable(boxTopStack);

			leftStack.setExpandImageDrawable(view.getResources().getDrawable(expand));
			rightStack.setExpandImageDrawable(view.getResources().getDrawable(expand));
			rightStack.setExpandImageDrawable(view.getResources().getDrawable(expand));
			statusBar.setBackgroundDrawable(view.getResources().getDrawable(boxTop));
			//infoBar.setBackgroundDrawable(view.getResources().getDrawable(boxBottom));

			paintText.setColor(textColor);
			paintSubText.setColor(textColor);
			paintSmallText.setColor(textColor);
			paintSmallSubText.setColor(textColor);

	
			topText.setShadowColor(textShadowColor);
			leftStack.setShadowColor(textShadowColor);
			rightStack.setShadowColor(textShadowColor);
			velStack.setShadowColor(textShadowColor);

			paintText.setFakeBoldText(textBold);
			paintSubText.setFakeBoldText(textBold);
			paintSmallText.setFakeBoldText(textBold);
			paintSmallSubText.setFakeBoldText(textBold);
			
			rightStack.invalidate();
			velStack.invalidate();
			leftStack.invalidate();
			statusBar.invalidate();
			//infoBar.invalidate();
		}
	}
	
	
	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings drawSettings) {
		updateColorShadowsOfText(drawSettings);
		// update data on draw
		rightStack.updateInfo(drawSettings);
		velStack.updateInfo(drawSettings);
		leftStack.updateInfo(drawSettings);
		lanesControl.updateInfo(drawSettings);
		alarmControl.updateInfo(drawSettings);
		for (int i = 0; i < statusBar.getChildCount(); i++) {
			View v = statusBar.getChildAt(i);
			if (v instanceof UpdateableWidget) {
				((UpdateableWidget) v).updateInfo(drawSettings);
			}
		}
		/*for (int i = 0; i < infoBar.getChildCount(); i++) {
			BaseMapWidget v = (BaseMapWidget) infoBar.getChildAt(i);
			if (v instanceof UpdateableWidget) {
				((UpdateableWidget) v).updateInfo(drawSettings);
			}
		}*/
		
	}
	
	public StackWidgetView getRightStack() {
		return rightStack;
	}
	
	public StackWidgetView getVelStack() {
		return velStack;
	}
	
	public TurnStackWidgetView getLeftStack() {
		return leftStack;
	}

	
	@Override
	public void destroyLayer() {
	}

	@Override
	public boolean drawInScreenPixels() {
		return true;
	}
	
	
	public View getProgressBar() {
		return progressBar;
	}

	private static class ConfigLayout extends FrameLayout implements UpdateableWidget {
		private ImageViewWidget config;

		private ConfigLayout(Context c, ImageViewWidget config) {
			super(c);
			this.config = config;
		}

		@Override
		public boolean updateInfo(DrawSettings drawSettings) {
			return config.updateInfo(drawSettings);
		}
	}
	
	private View createBackToLocationWithProgress(final MapActivity map) {
		final OsmandMapTileView view = map.getMapView();

		final Drawable backToLoc = map.getResources().getDrawable(
				R.drawable.back_to_loc_white);
		final Drawable backToLocWhite = map.getResources().getDrawable(
				R.drawable.back_to_loc_white);
		final Drawable backToLocDisabled = map.getResources().getDrawable(
				R.drawable.la_backtoloc_disabled_white);
		final Drawable backToLocDisabledWhite = map.getResources().getDrawable(
				R.drawable.la_backtoloc_disabled_white);
		final Drawable backToLocTracked = map.getResources().getDrawable(
				R.drawable.back_to_loc_tracked_white);
		final Drawable backToLocTrackedWhite = map.getResources().getDrawable(
				R.drawable.back_to_loc_tracked_white);

		FrameLayout.LayoutParams fparams = new FrameLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

		ImageViewWidget whereIam = new ImageViewWidget(map) {
			Drawable lastDrawable = null;

			@Override
			public boolean updateInfo(DrawSettings drawSettings) {
				boolean nightMode = drawSettings == null ? false : drawSettings
						.isNightMode();
				boolean enabled = map.getMyApplication().getLocationProvider()
						.getLastKnownLocation() != null;
				boolean tracked = map.getMapViewTrackingUtilities()
						.isMapLinkedToLocation();
				Drawable d;
				if (!enabled) {
					d = nightMode ? backToLocDisabledWhite : backToLocDisabled;
				} else if (tracked) {
					d = nightMode ? backToLocTrackedWhite : backToLocTracked;
				} else {
					d = nightMode ? backToLocWhite : backToLoc;
				}
				if (d != lastDrawable) {
					lastDrawable = d;
					setImageDrawable(d);
				}
				return true;
			}
		};
		//whereIam.setPadding((int) (5 * scaleCoefficient), 0,(int) (5 * scaleCoefficient), 0);
		whereIam.setBackgroundDrawable(map.getResources().getDrawable(
				R.drawable.back_to_loc));

		FrameLayout fl = new ConfigLayout(view.getContext(), whereIam);
		fl.addView(whereIam, fparams);
		fparams = new FrameLayout.LayoutParams(view.getResources()
				.getDrawable(R.drawable.back_to_loc).getMinimumWidth(), view
				.getResources().getDrawable(R.drawable.back_to_loc)
				.getMinimumHeight());
		progressBar = new View(view.getContext());
		fl.addView(progressBar, fparams);

		fl.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				map.getMapViewTrackingUtilities().backToLocationImpl();
			}
		});
		return fl;
	}
	
	private View createConfiguration(){
		final OsmandMapTileView view = map.getMapView();
		
		FrameLayout.LayoutParams fparams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		final Drawable config = view.getResources().getDrawable(R.drawable.map_config_white);
		final Drawable configWhite = view.getResources().getDrawable(R.drawable.map_config_white);
		ImageViewWidget configuration = new ImageViewWidget(map) {
			private boolean nm;
			
			@Override
			public boolean updateInfo(DrawSettings drawSettings) {
				boolean nightMode = drawSettings != null && drawSettings.isNightMode();
				if(nightMode != this.nm) {
					this.nm = nightMode;
					setImageDrawable(nightMode ? configWhite : config);
					return true;
				}
				return false;
			}
		};
		configuration.setBackgroundDrawable(config);
		FrameLayout fl = new ConfigLayout(view.getContext(), configuration) ;
		fl.addView(configuration, fparams);
		fparams = new FrameLayout.LayoutParams(config.getMinimumWidth(), config.getMinimumHeight());
		progressBar = new View(view.getContext());
		fl.addView(progressBar, fparams);
		fl.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openViewConfigureDialog();
			}
		});
		return fl;
	}
	private View createLayer(){
//		final Drawable globusDrawable = view.getResources().getDrawable(R.drawable.map_globus);
//		final Drawable globusDrawableWhite = view.getResources().getDrawable(R.drawable.map_globus_white);
		final Drawable layerDrawable = view.getResources().getDrawable(R.drawable.map_layers_black);
		final Drawable layerDrawableWhite = view.getResources().getDrawable(R.drawable.map_layers_white);
		
		ImageView layers = new ImageViewWidget(view.getContext()) {
			private boolean nightMode;

			@Override
			public boolean updateInfo(DrawSettings drawSettings) {
				boolean nightMode = drawSettings == null ? false : drawSettings.isNightMode();
				if(nightMode != this.nightMode) {
					this.nightMode = nightMode;
					setImageDrawable(nightMode ? layerDrawableWhite : layerDrawable);
					return true;
				}
				return false;
			}
		};;
		layers.setImageDrawable(layerDrawable);
		layers.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				map.getMapLayers().openLayerSelectionDialog(view);
				//map.getMapLayers().selectMapLayer(view);
			}
		});
		return layers;
	}
	


	public static String getStringPropertyName(Context ctx, String propertyName, String defValue) {
		try {
			Field f = R.string.class.getField("rendering_attr_" + propertyName + "_name");
			if (f != null) {
				Integer in = (Integer) f.get(null);
				return ctx.getString(in);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		return defValue;
	}

	public static String getStringPropertyDescription(Context ctx, String propertyName, String defValue) {
		try {
			Field f = R.string.class.getField("rendering_attr_" + propertyName + "_description");
			if (f != null) {
				Integer in = (Integer) f.get(null);
				return ctx.getString(in);
			}
		} catch (Exception e) {
			//e.printStackTrace();
			System.err.println(e.getMessage());
		}
		return defValue;
	}
}
