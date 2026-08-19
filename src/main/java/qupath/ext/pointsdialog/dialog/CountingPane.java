/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.ext.pointsdialog.dialog;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PathObjectLabels;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Component for creating and modifying point objects.
 * <p>
 * Each point annotation is shown as a row that can be expanded (lazily) into its
 * individual points. Selecting a point row centers the current viewer on that point
 * (without changing magnification), and deleting a point row removes just that point
 * from the annotation's ROI - the annotation itself is never split apart.
 *
 * @author Pete Bankhead
 *
 */
class CountingPane implements PathObjectSelectionListener, PathObjectHierarchyListener {

	private QuPathGUI qupath;
	
	private BorderPane pane = new BorderPane();
	
	private PathObjectHierarchy hierarchy;
	
	private TreeView<Object> treeCounts;
	private TreeItem<Object> rootItem = new TreeItem<>();

	/**
	 * One AnnotationTreeItem per point annotation currently shown, keyed by the PathObject itself.
	 * Reused across refreshes so that expanded/collapsed state (and TreeItem identity, and hence
	 * selection) survives hierarchy change events where possible.
	 */
	private final Map<PathObject, AnnotationTreeItem> itemMap = new LinkedHashMap<>();
	
	private Action btnAdd = new Action(QuPathResources.getString("Commands.CountingPane.add"), e -> {
		PathObject pathObjectCounts = PathObjects.createAnnotationObject(ROIs.createPointsROI(ImagePlane.getDefaultPlane()));
		hierarchy.addObject(pathObjectCounts);
//		hierarchy.fireChangeEvent(pathObjectCounts.getParent());
		hierarchy.getSelectionModel().setSelectedObject(pathObjectCounts);
//		promptToSetProperties();
	});
	private Action btnEdit = new Action(QuPathResources.getString("Commands.CountingPane.edit"), e -> promptToSetProperties());
	private Action btnDelete = new Action(QuPathResources.getString("Commands.CountingPane.delete"), e -> deleteSelected());
	
	/**
	 * Create point annotations for all available classifications
	 */
	private Action btnCreateForClasses = new Action(QuPathResources.getString("Commands.CountingPane.createPointsForAllClasses"), e -> {
		var viewer = qupath.getViewer();
		var hierarchy = viewer.getHierarchy();
		var availableClasses = qupath.getAvailablePathClasses()
				.stream()
				.filter(p -> p != null && p != PathClass.NULL_CLASS)
				.toList();
		if (hierarchy == null || availableClasses.isEmpty())
			return;
		var plane = viewer.getImagePlane();
		var pathObjects = new ArrayList<PathObject>();
		for (PathClass pathClass : availableClasses) {
			pathObjects.add(PathObjects.createAnnotationObject(ROIs.createPointsROI(plane), pathClass));
		}
		hierarchy.addObjects(pathObjects);
	});
	
	
	public CountingPane(final QuPathGUI qupath, final PathObjectHierarchy hierarchy) {
		
		this.qupath = qupath;
		treeCounts = new TreeView<>(rootItem);
		treeCounts.setShowRoot(false);
		rootItem.setExpanded(true);
		
		setHierarchy(hierarchy);
		
		treeCounts.getSelectionModel().selectedItemProperty().addListener((e, oldSelection, selectedItem) -> {
			updateSelectionFromTree(selectedItem == null ? null : selectedItem.getValue());
		});
		
		// Make buttons
		GridPane paneMainButtons = GridPaneUtils.createColumnGridControls(
				ActionUtils.createButton(btnAdd),
				ActionUtils.createButton(btnEdit),
				ActionUtils.createButton(btnDelete)
				);
		
		// Add additional options
		var popup = new ContextMenu();
		popup.getItems()
			.add(ActionUtils.createMenuItem(btnCreateForClasses));
		
		var paneButtons = new BorderPane(paneMainButtons);
		Button btnMore = GuiTools.createMoreButton(popup, Side.RIGHT);
		paneButtons.setRight(btnMore);
				
		// Add double-click listener (only meaningful for a whole annotation, not a single point)
		treeCounts.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1)
				promptToSetProperties();
		});
		// Support Delete/Backspace from the tree itself, matching the button
		treeCounts.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
				deleteSelected();
				e.consume();
			}
		});

		ContextMenu menu = new ContextMenu();
		Menu menuSetClass = new Menu(QuPathResources.getString("Commands.CountingPane.setClassification"));
		MenuItem miCopy = new MenuItem(QuPathResources.getString("Commands.CountingPane.copyToClipboard"));
		// Not localized - this is a new action without an existing resource key
		MenuItem miDeletePoint = new MenuItem("Delete point");
		miDeletePoint.setOnAction(e -> {
			var selectedItem = treeCounts.getSelectionModel().getSelectedItem();
			if (selectedItem != null && selectedItem.getValue() instanceof PointEntry entry)
				deletePoint(entry);
		});
		menu.setOnShowing(e -> {
			menuSetClass.getItems().setAll(
					qupath.getAvailablePathClasses().stream()
					.map(p -> createPathClassMenuItem(p))
					.toList());
			var selectedItem = treeCounts.getSelectionModel().getSelectedItem();
			miDeletePoint.setVisible(selectedItem != null && selectedItem.getValue() instanceof PointEntry);
		});
		miCopy.setOnAction(e -> copyCoordinatesToClipboard(resolvePathObject(treeCounts.getSelectionModel().getSelectedItem())));
		miCopy.disableProperty().bind(treeCounts.getSelectionModel().selectedItemProperty().isNull());
		menuSetClass.disableProperty().bind(treeCounts.getSelectionModel().selectedItemProperty().isNull());
		menu.getItems().addAll(menuSetClass, miCopy, miDeletePoint);
		treeCounts.setContextMenu(menu);
		
		treeCounts.setCellFactory(v -> new CountingTreeCell());
		
		
		PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> treeCounts.refresh());
		
		// Add to panel
		BorderPane panelList = new BorderPane();
		panelList.setCenter(treeCounts);
		panelList.setBottom(paneButtons);
//		panelList.setBorder(BorderFactory.createTitledBorder("Counts"));		
		
		pane.setCenter(panelList);
	}
	
	
	MenuItem createPathClassMenuItem(PathClass pathClass) {
		var mi = new MenuItem(pathClass.toString());
		mi.setMnemonicParsing(false); // Fix display of underscores in menu items
		var color = pathClass.getColor();
		var rect = new Rectangle(8, 8, color == null ? ColorToolsFX.TRANSLUCENT_WHITE_FX : ColorToolsFX.getCachedColor(color));
		mi.setGraphic(rect);
		mi.setOnAction(e -> {
			var pathObject = resolvePathObject(treeCounts.getSelectionModel().getSelectedItem());
			if (pathObject == null)
				return;
			if (pathClass == PathClass.NULL_CLASS)
				pathObject.resetPathClass();
			else
				pathObject.setPathClass(pathClass);
			if (hierarchy != null)
				hierarchy.fireObjectClassificationsChangedEvent(mi, Collections.singleton(pathObject));
		});
		return mi;
	}
	
	
	public Pane getPane() {
		return pane;
	}
	
	/**
	 * The underlying tree, in case direct access is useful (e.g. for testing or further customization).
	 * Values are either a {@link PathObject} (an annotation row) or an opaque point-row object -
	 * prefer {@link #getPathObjects()} / {@link #getSelectedPathObjects()} where possible.
	 */
	public TreeView<Object> getTreeView() {
		return treeCounts;
	}
	
	
	public List<PathObject> getPathObjects() {
		List<PathObject> result = new ArrayList<>();
		for (var item : rootItem.getChildren())
			result.add(((AnnotationTreeItem)item).getPathObject());
		return result;
	}
	
	/**
	 * The point annotations implied by the current tree selection.
	 * A selected point row resolves to its parent annotation, so this is safe to use anywhere
	 * the old {@code ListView<PathObject>} selection was used (e.g. deciding what to save).
	 */
	public List<PathObject> getSelectedPathObjects() {
		var result = new LinkedHashSet<PathObject>();
		for (var item : treeCounts.getSelectionModel().getSelectedItems()) {
			var po = resolvePathObject(item);
			if (po != null)
				result.add(po);
		}
		return new ArrayList<>(result);
	}
	
	
	private static PathObject resolvePathObject(TreeItem<Object> item) {
		if (item == null)
			return null;
		var value = item.getValue();
		if (value instanceof PathObject po)
			return po;
		if (value instanceof PointEntry entry)
			return entry.parent;
		return null;
	}
	
	
	public void setHierarchy(PathObjectHierarchy hierarchy) {
		if (this.hierarchy == hierarchy)
			return;
		if (this.hierarchy != null) {
			this.hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
			this.hierarchy.removeListener(this);
		}
		this.hierarchy = hierarchy;
		
		if (this.hierarchy != null) {
			PathObjectSelectionModel model = this.hierarchy.getSelectionModel();
			model.addPathObjectSelectionListener(this);
			this.hierarchy.addListener(this);
		}
		
		// Force update - rebuilds the tree and restores the current hierarchy selection, if any
		hierarchyChanged(null);
	}
	
	
	public static void copyCoordinatesToClipboard(PathObject pathObject) {
//		PathObject pathObject = viewer.getPathObjectHierarchy().getSelectionModel().getSelectedPathObject();
		if (pathObject == null || !pathObject.hasROI() || !(pathObject.getROI() instanceof PointsROI)) {
			Dialogs.showErrorMessage(
					QuPathResources.getString("Commands.CountingPane.copyPointsToClipboard"),
					QuPathResources.getString("Commands.CountingPane.noPointsSelected")
			);
			return;
		}
		StringBuilder sb = new StringBuilder();
		String name = pathObject.getDisplayedName();
		PointsROI points = (PointsROI)pathObject.getROI();
		for (Point2 p : points.getAllPoints())
			sb.append(name).append("\t").append(p.getX()).append("\t").append(p.getY()).append("\n");

		StringSelection stringSelection = new StringSelection(sb.toString());
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(stringSelection, null);
	}
	
	
	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
		var currentItem = treeCounts.getSelectionModel().getSelectedItem();
		var currentValue = currentItem == null ? null : currentItem.getValue();
		
		// Ignore changes we already reflect in the tree - avoids fighting with a point-level
		// selection (which sets the *parent* as the hierarchy selection but should keep the
		// point row selected in the tree), and avoids re-entrant loops via updateSelectionFromTree.
		if (currentValue == pathObjectSelected)
			return;
		if (currentValue instanceof PointEntry pe && pe.parent == pathObjectSelected)
			return;
		
		boolean hasPoints = pathObjectSelected != null && PathObjectTools.hasPointROI(pathObjectSelected);
		if (hasPoints) {
			var item = itemMap.get(pathObjectSelected);
			if (item != null) {
				treeCounts.getSelectionModel().select(item);
				treeCounts.scrollTo(treeCounts.getRow(item));
			}
		} else
			treeCounts.getSelectionModel().clearSelection();
	}


	private void promptToSetProperties() {
		var pathObjectSelected = resolvePathObject(treeCounts.getSelectionModel().getSelectedItem());
		if (pathObjectSelected != null && PathObjectTools.hasPointROI(pathObjectSelected)) {
			GuiTools.promptToSetActiveAnnotationProperties(hierarchy);
		}
	}
	
	
	private void updateButtonStates(Object value) {
		boolean isAnnotation = value instanceof PathObject po && PathObjectTools.hasPointROI(po);
		boolean isPoint = value instanceof PointEntry;
		btnEdit.setDisabled(!isAnnotation);
		btnDelete.setDisabled(!isAnnotation && !isPoint);
	}
	
	
	private void updateSelectionFromTree(Object value) {
		updateButtonStates(value);
		if (value instanceof PathObject po) {
			if (hierarchy != null && hierarchy.getSelectionModel().getSelectedObject() != po)
				hierarchy.getSelectionModel().setSelectedObject(po);
		} else if (value instanceof PointEntry entry) {
			if (hierarchy != null && hierarchy.getSelectionModel().getSelectedObject() != entry.parent)
				hierarchy.getSelectionModel().setSelectedObject(entry.parent);
			centerOnPoint(entry);
		}
	}
	
	
	/**
	 * Center the current viewer on a point, without changing the current zoom/magnification.
	 */
	private void centerOnPoint(PointEntry entry) {
		Point2 point = entry.getPoint();
		if (point == null || qupath == null)
			return;
		var viewer = qupath.getViewer();
		if (viewer == null)
			return;
		viewer.setCenterPixelLocation(point.getX(), point.getY());
	}
	
	
	private void deleteSelected() {
		var selectedItem = treeCounts.getSelectionModel().getSelectedItem();
		if (selectedItem == null)
			return;
		var value = selectedItem.getValue();
		if (value instanceof PointEntry entry)
			deletePoint(entry);
		else if (value instanceof PathObject pathObjectSelected && PathObjectTools.hasPointROI(pathObjectSelected))
			GuiTools.promptToRemoveSelectedObject(pathObjectSelected, hierarchy);
	}
	
	
	/**
	 * Remove a single point from its parent annotation's ROI, in place - mirroring what an
	 * alt-click on the viewer does (see {@code PointsToolEventHandler.removePoint}). The
	 * annotation object itself (and its classification, lock state, etc.) is preserved;
	 * only its ROI is replaced with a new, immutable {@link PointsROI} missing this one point.
	 */
	private void deletePoint(PointEntry entry) {
		if (hierarchy == null)
			return;
		PathObject pathObject = entry.parent;
		ROI roi = pathObject.getROI();
		if (!(roi instanceof PointsROI pointsRoi))
			return;
		List<Point2> pointsList = new ArrayList<>(pointsRoi.getAllPoints());
		if (entry.index < 0 || entry.index >= pointsList.size())
			return; // stale index - the ROI must already have changed elsewhere
		pointsList.remove(entry.index);
		ROI newRoi = ROIs.createPointsROI(pointsList, roi.getImagePlane());
		((PathROIObject)pathObject).setROI(newRoi);

		// What was 'next' now sits at the same index the deleted point occupied; clamp to the
		// new last point if we deleted the last one, or select nothing if none remain.
		int nextIndex = pointsList.isEmpty() ? -1 : Math.min(entry.index, pointsList.size() - 1);

		hierarchy.updateObject(pathObject, false);

		// hierarchy.updateObject() synchronously rebuilds the tree (and, via selectedPathObjectChanged,
		// falls back to selecting the parent annotation row - see reload()). Override that here.
		if (nextIndex >= 0)
			selectPoint(pathObject, nextIndex);
	}

	/**
	 * Select a specific point row within an annotation's (already-reloaded) tree children,
	 * so a delete restores the user's position in the list instead of collapsing selection
	 * back up to the parent annotation row.
	 */
	private void selectPoint(PathObject pathObject, int index) {
		var item = itemMap.get(pathObject);
		if (item == null)
			return;
		var children = item.getChildren();
		if (index < 0 || index >= children.size())
			return;
		var target = children.get(index);
		treeCounts.getSelectionModel().select(target);
		treeCounts.scrollTo(treeCounts.getRow(target));
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}
		if (hierarchy == null) {
			itemMap.clear();
			rootItem.getChildren().clear();
			return;
		}
		
		syncTreeWithHierarchy();
		
		// We want to retain selection status
		var selected = hierarchy.getSelectionModel().getSelectedObject();
		var allSelected = hierarchy.getSelectionModel().getSelectedObjects();
		selectedPathObjectChanged(selected, null, allSelected);
	}
	
	
	/**
	 * Rebuild the top-level rows to match the current point annotations, reusing existing
	 * {@link AnnotationTreeItem}s (and hence expanded state / selection) where a PathObject
	 * is still present. Any row that is currently expanded gets its point children reloaded
	 * so counts and coordinates stay current; collapsed rows are just marked stale so they
	 * reload lazily next time they're opened.
	 */
	private void syncTreeWithHierarchy() {
		Collection<PathObject> newList = hierarchy == null ? Collections.emptyList() : hierarchy.getAllPointAnnotations();
		
		itemMap.keySet().retainAll(newList);
		
		List<TreeItem<Object>> orderedChildren = new ArrayList<>();
		for (PathObject po : newList) {
			AnnotationTreeItem item = itemMap.computeIfAbsent(po, AnnotationTreeItem::new);
			if (item.isExpanded())
				item.reload();
			else
				item.invalidate();
			orderedChildren.add(item);
		}
		if (!rootItem.getChildren().equals(orderedChildren))
			rootItem.getChildren().setAll(orderedChildren);
		treeCounts.refresh();
	}
	
	
	/**
	 * A leaf tree-row referring to a single point within a parent annotation's {@link PointsROI}.
	 * The point itself is looked up on demand (by index, from the parent's current ROI) rather
	 * than cached, so it can't go stale if the ROI changes elsewhere.
	 */
	private static class PointEntry {
		
		private final PathObject parent;
		private final int index;
		
		private PointEntry(PathObject parent, int index) {
			this.parent = parent;
			this.index = index;
		}
		
		private Point2 getPoint() {
			if (parent.getROI() instanceof PointsROI pointsRoi) {
				List<Point2> points = pointsRoi.getAllPoints();
				if (index >= 0 && index < points.size())
					return points.get(index);
			}
			return null;
		}
		
	}
	
	
	/**
	 * A top-level tree row for a point annotation, lazily populated with one child
	 * {@link PointEntry} row per point the first time it's expanded (following the standard
	 * JavaFX lazy-TreeItem pattern - {@link #isLeaf()} must not force loading, since it's what
	 * JavaFX consults to decide whether to draw a disclosure arrow in the first place).
	 */
	private static class AnnotationTreeItem extends TreeItem<Object> {
		
		private boolean loaded = false;
		
		private AnnotationTreeItem(PathObject pathObject) {
			super(pathObject);
		}
		
		private PathObject getPathObject() {
			return (PathObject)getValue();
		}
		
		private void invalidate() {
			loaded = false;
			super.getChildren().clear();
		}
		
		private void reload() {
			List<TreeItem<Object>> children = new ArrayList<>();
			if (getPathObject().getROI() instanceof PointsROI pointsRoi) {
				List<Point2> points = pointsRoi.getAllPoints();
				for (int i = 0; i < points.size(); i++)
					children.add(new TreeItem<>(new PointEntry(getPathObject(), i)));
			}
			super.getChildren().setAll(children);
			loaded = true;
		}
		
		@Override
		public ObservableList<TreeItem<Object>> getChildren() {
			if (!loaded)
				reload();
			return super.getChildren();
		}
		
		@Override
		public boolean isLeaf() {
			// Must not trigger loading - only used to decide whether to show a disclosure arrow
			var roi = getPathObject().getROI();
			return !(roi instanceof PointsROI pointsRoi) || pointsRoi.getNumPoints() == 0;
		}
		
	}
	
	
	/**
	 * Renders either a {@link PathObject} row (same look as the previous list, via
	 * {@link PathObjectLabels.PathObjectMiniPane}) or a {@link PointEntry} row (plain
	 * 1-based index and coordinates).
	 */
	private static class CountingTreeCell extends TreeCell<Object> {
		
		private final PathObjectLabels.PathObjectMiniPane miniPane =
				new PathObjectLabels.PathObjectMiniPane(p -> p.toString().replace(" (Points)", ""));
		
		@Override
		protected void updateItem(Object value, boolean empty) {
			super.updateItem(value, empty);
			if (empty || value == null) {
				setText(null);
				setGraphic(null);
				miniPane.setPathObject(null);
				return;
			}
			if (value instanceof PathObject po) {
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
				miniPane.setPathObject(po);
				setGraphic(miniPane.getNode());
			} else if (value instanceof PointEntry entry) {
				setContentDisplay(ContentDisplay.TEXT_ONLY);
				setGraphic(null);
				Point2 point = entry.getPoint();
				if (point == null)
					setText(null);
				else
					setText(String.format("%d:  x=%.1f, y=%.1f", entry.index + 1, point.getX(), point.getY()));
			}
		}
		
	}
	
	
}
