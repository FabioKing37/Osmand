package net.osmand.plus;

import android.app.Activity;
import android.view.Window;
import net.osmand.plus.activities.DownloadIndexActivity;
import net.osmand.plus.activities.FavouritesActivity;
import net.osmand.plus.activities.LocalIndexesActivity;
import net.osmand.plus.activities.MainMenuActivity;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.PluginsActivity;
import net.osmand.plus.activities.SettingsActivity;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.api.SettingsAPI;

public class OsmAndAppCustomization {
	
	protected OsmandApplication app;

	public void setup(OsmandApplication app) {
		this.app = app;
	}
	
	public OsmandSettings createSettings(SettingsAPI api) {
		return new OsmandSettings(app, api);
	}

	public boolean checkExceptionsOnStart() {
		return true;
	}

	public boolean showFirstTimeRunAndTips(boolean firstTime, boolean appVersionChanged) {
		return true;
	}

	public boolean checkBasemapDownloadedOnStart() {
		return true;
	}

	public void customizeMainMenu(Window window, Activity activity) {
	}
	
	
	
	public Class<? extends Activity> getSettingsActivity(){
		return SettingsActivity.class;
	}
	
	public Class<MapActivity> getMapActivity(){
		return MapActivity.class;
	}
	
	public Class<SearchActivity> getSearchActivity(){
		return SearchActivity.class;
	}
	
	public Class<FavouritesActivity> getFavoritesActivity(){
		return FavouritesActivity.class;
	}

	public Class<MainMenuActivity> getMainMenuActivity() {
		return MainMenuActivity.class;
	}
	
	public Class<? extends Activity> getDownloadIndexActivity() {
		return DownloadIndexActivity.class;
	}
	
	public Class<? extends Activity> getPluginsActivity() {
		return PluginsActivity.class;
	}
	
	public Class<? extends Activity> getLocalIndexActivity() {
		return LocalIndexesActivity.class;
	}

}
