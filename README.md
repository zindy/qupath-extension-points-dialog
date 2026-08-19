# qupath-extension-points-dialog (QuPath v0.8.x)

A QuPath v0.8.x extension that replaces the built-in points/counting dialog with one that lets you 
expand each point annotation to inspect and interact with its individual points - for now, 
focusing on and deleting single points.

Once installed, the dialog is available under **Extensions > PointsDialog > Open points dialog**. 
It also opens automatically if you use QuPath's original counting-tool button or menu entry, so 
you get the new dialog either way without changing your existing workflow. Removing the extension 
restores QuPath's original points dialog.

The dialog offers a panel for creating, classifying, loading, saving, and deleting point 
annotations alongside the current image. Unlike QuPath's original points dialog (as of v0.7.0), 
points are shown in a tree view: each annotation can be expanded to reveal its individual points 
and their image coordinates.

Selecting a point automatically centres it in the QuPath viewer, making it easy to quickly check 
that all points are correct (for example, when preparing training data for a classifier). 
Selected points can be removed either with the "DEL" key or the "Delete" button.

## Build the extension

You don't need to install Gradle separately — the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) handles that.

Open a command prompt, navigate to the project root, and run:

```bash
gradlew build
```

The built extension jar will be in `build/libs`. Drag it onto QuPath to install it — you'll be prompted to create a user directory if you don't already have one.

## Configure the extension

Unlike the original QuPath extension template, here the version is set in a separate [VERSION](VERSION) file rather than directly in `build.gradle.kts`.

A build and publish action is triggered *automatically* on GitHub when a git tag is pushed that matches the VERSION file (*sans* the `-SNAPSHOT` part) — see [build.yml](.github/workflows/build.yml).

## Run QuPath + the extension during development

### 1. Make sure you have Java installed

QuPath uses Java 25 (a Long Term Support release). Download it from https://adoptium.net/

### 2. Get QuPath's source code

Instructions at https://qupath.readthedocs.io/en/stable/docs/reference/building.html

### 3. Create an `include-extra` file

In the root of the QuPath source (not this extension), create a file called `include-extra` with:

```
[includeBuild]
../qupath-extension-pointsdialog

[dependencies]
io.github.qupath:qupath-extension-pointsdialog
```

### 4. Run QuPath

```bash
gradlew run
```

QuPath will launch with the extension installed. Check **Extensions** in the menu bar to confirm.

## IDE setup

QuPath is developed in IntelliJ. You can import this extension the same way, and create a [Run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html) pointing to `gradlew run`.

## Releases

To publish a new version, push a git tag matching the [VERSION](VERSION) file (sans the `-SNAPSHOT` part):

```bash
git tag -a v0.1.0 -m "Release version 0.1.0"
git push origin v0.1.0
```

Or, from the extension's folder, this bash one-liner reads the version straight from the file:

```bash
VERSION=$(cat VERSION | sed 's/-SNAPSHOT//'); git tag -a "v$VERSION" -m "Release version $VERSION"; git push origin "v$VERSION"
```

Pushing the tag triggers [build.yml](.github/workflows/build.yml), which builds the extension and creates a release with the jar, sources, and javadoc attached. Once published, users can install it automatically via QuPath's extension manager.

Once the new version is published, increment the VERSION file to start the next round of development.

See https://qupath.readthedocs.io/en/0.5/docs/intro/extensions.html#installing-extensions for details.

## Getting help

For questions about QuPath and creating extensions, use the forum at https://forum.image.sc/tag/qupath

## License

The `CountingDialogCommand` and `CountingPane` classes in this extension are derived from [QuPath](https://github.com/qupath/qupath), which is available under the GPL v3. This extension is therefore also licensed under the [GPL v3](https://www.gnu.org/licenses/gpl-3.0.html).
