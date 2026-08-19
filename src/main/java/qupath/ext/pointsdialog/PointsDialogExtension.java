package qupath.ext.pointsdialog;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.pointsdialog.dialog.CountingDialogCommand;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.ResourceBundle;


/**
 * This is a demo to provide a pointsdialog for creating a new QuPath extension.
 * <p>
 * It doesn't do much - it just shows how to add a menu item and a preference.
 * See the code and comments below for more info.
 * <p>
 * <b>Important!</b> For your extension to work in QuPath, you need to make sure the name &amp; package
 * of this class is consistent with the file
 * <pre>
 *     /resources/META-INF/services/qupath.lib.gui.extensions.QuPathExtension
 * </pre>
 */
public class PointsDialogExtension implements QuPathExtension {
	// TODO: add and modify strings to this resource bundle as needed
	/**
	 * A resource bundle containing all the text used by the extension. This may be useful for translation to other languages.
	 * Note that this is optional and you can define the text within the code and FXML files that you use.
	 */
	private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.pointsdialog.ui.strings");
	private static final Logger logger = LoggerFactory.getLogger(PointsDialogExtension.class);

	/**
	 * Display name for your extension
	 * TODO: define this
	 */
	private static final String EXTENSION_NAME = resources.getString("name");

	/**
	 * Short description, used under 'Extensions > Installed extensions'
	 * TODO: define this
	 */
	private static final String EXTENSION_DESCRIPTION = resources.getString("description");

	/**
	 * QuPath version that the extension is designed to work with.
	 * This allows QuPath to inform the user if it seems to be incompatible.
	 * TODO: define this
	 */
	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.5.0");

	/**
	 * Flag whether the extension is already installed (might not be needed... but we'll do it anyway)
	 */
	private boolean isInstalled = false;

	/**
	 * A 'persistent preference' - showing how to create a property that is stored whenever QuPath is closed.
	 * This preference will be managed in the main QuPath GUI preferences window.
	 */
	private static final BooleanProperty enableExtensionProperty = PathPrefs.createPersistentPreference(
			"enableExtension", true);

	/**
	 * The command that actually opens the counting/points dialog.
	 * Created lazily (once) in {@link #addMenuItem(QuPathGUI)} and reused on every click,
	 * since {@link CountingDialogCommand} keeps track of its own dialog Stage internally.
	 */
	private CountingDialogCommand countingDialogCommand;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addPreferenceToPane(qupath);
		addMenuItem(qupath);
	}

	/**
	 * PointsDialog showing how to add a persistent preference to the QuPath preferences pane.
	 * The preference will be in a section of the preference pane based on the
	 * category you set. The description is used as a tooltip.
	 * @param qupath The currently running QuPathGUI instance.
	 */
	private void addPreferenceToPane(QuPathGUI qupath) {
        var propertyItem = new PropertyItemBuilder<>(enableExtensionProperty, Boolean.class)
				.name(resources.getString("menu.enable"))
				.category("PointsDialog extension")
				.description("Enable the demo extension")
				.build();
		qupath.getPreferencePane()
				.getPropertySheet()
				.getItems()
				.add(propertyItem);
	}


	/**
	 * Add a menu item that opens the counting/points dialog.
	 * @param qupath The QuPath GUI
	 */
	private void addMenuItem(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		MenuItem menuItem = new MenuItem(resources.getString("menu.openDialog"));
		menuItem.setOnAction(e -> {
			if (countingDialogCommand == null)
				countingDialogCommand = new CountingDialogCommand(qupath);
			countingDialogCommand.run();
		});
		menuItem.disableProperty().bind(enableExtensionProperty.not());
		menu.getItems().add(menuItem);
	}


	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}
}
