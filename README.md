# 茶商贷 Flutter WebView Plugin

# Fork from [Flutter WebView Plugin](https://github.com/fluttercommunity/flutter_webview_plugin)
[![pub package](https://img.shields.io/pub/v/flutter_webview_plugin.svg)](https://pub.dartlang.org/packages/flutter_webview_plugin)

Plugin that allows Flutter to communicate with a native WebView.

**_Warning:_**
The webview is not integrated in the widget tree, it is a native view on top of the flutter view.
You won't be able see snackbars, dialogs, or other flutter widgets that would overlap with the region of the screen taken up by the webview.

## Getting Started

For help getting started with Flutter, view our online [documentation](http://flutter.io/).

#### iOS

In order for plugin to work correctly, you need to add new key to `ios/Runner/Info.plist`

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
    <key>NSAllowsArbitraryLoadsInWebContent</key>
    <true/>
</dict>
```

`NSAllowsArbitraryLoadsInWebContent` is for iOS 10+ and `NSAllowsArbitraryLoads` for iOS 9.


### How it works

#### Launch WebView Fullscreen with Flutter navigation

```dart
new MaterialApp(
      routes: {
        "/": (_) => new WebviewScaffold(
          url: "https://www.google.com",
          appBar: new AppBar(
            title: new Text("Widget webview"),
          ),
        ),
      },
    );
```

Optional parameters `hidden` and `initialChild` are available so that you can show something else while waiting for the page to load.
If you set `hidden` to true it will show a default CircularProgressIndicator. If you additionally specify a Widget for initialChild
you can have it display whatever you like till page-load.

e.g. The following will show a read screen with the text 'waiting.....'.
```dart
return new MaterialApp(
  title: 'Flutter WebView Demo',
  theme: new ThemeData(
    primarySwatch: Colors.blue,
  ),
  routes: {
    '/': (_) => const MyHomePage(title: 'Flutter WebView Demo'),
    '/widget': (_) => new WebviewScaffold(
      url: selectedUrl,
      appBar: new AppBar(
        title: const Text('Widget webview'),
      ),
      withZoom: true,
      withLocalStorage: true,
      hidden: true,
      initialChild: Container(
        color: Colors.redAccent,
        child: const Center(
          child: Text('Waiting.....'),
        ),
      ),
    ),
  },
);
```

`FlutterWebviewPlugin` provide a singleton instance linked to one unique webview,
so you can take control of the webview from anywhere in the app

listen for events

```dart
final flutterWebviewPlugin = new FlutterWebviewPlugin();

flutterWebviewPlugin.onUrlChanged.listen((String url) {

});
```

#### Listen for scroll event in webview

```dart
final flutterWebviewPlugin = new FlutterWebviewPlugin();
flutterWebviewPlugin.onScrollYChanged.listen((double offsetY) { // latest offset value in vertical scroll
  // compare vertical scroll changes here with old value
});

flutterWebviewPlugin.onScrollXChanged.listen((double offsetX) { // latest offset value in horizontal scroll
  // compare horizontal scroll changes here with old value
});

````

Note: Do note there is a slight difference is scroll distance between ios and android. Android scroll value difference tends to be larger than ios devices.


#### Hidden WebView

```dart
final flutterWebviewPlugin = new FlutterWebviewPlugin();

flutterWebviewPlugin.launch(url, hidden: true);
```

#### Close launched WebView

```dart
flutterWebviewPlugin.close();
```

#### Webview inside custom Rectangle

```dart
final flutterWebviewPlugin = new FlutterWebviewPlugin();

flutterWebviewPlugin.launch(url,
  fullScreen: false,
  rect: new Rect.fromLTWH(
    0.0,
    0.0,
    MediaQuery.of(context).size.width,
    300.0,
  ),
);
```

#### Injecting custom code into the webview
Use `flutterWebviewPlugin.evalJavaScript(String code)`. This function must be run after the page has finished loading (i.e. listen to `onStateChanged` for events where state is `finishLoad`).

If you have a large amount of JavaScript to embed, use an asset file. Add the asset file to `pubspec.yaml`, then call the function like:

```dart
Future<String> loadJS(String name) async {
  var givenJS = rootBundle.loadString('assets/$name.js');
  return givenJS.then((String js) {
    flutterWebViewPlugin.onStateChanged.listen((viewState) async {
      if (viewState.type == WebViewState.finishLoad) {
        flutterWebViewPlugin.evalJavascript(js);
      }
    });
  });
}
```


### Webview Events

- `Stream<Null>` onDestroy
- `Stream<String>` onUrlChanged
- `Stream<WebViewStateChanged>` onStateChanged
- `Stream<double>` onScrollXChanged
- `Stream<double>` onScrollYChanged
- `Stream<String>` onError

**_Don't forget to dispose webview_**
`flutterWebviewPlugin.dispose()`

### Webview Functions

```dart
Future<Null> launch(String url, {
   Map<String, String> headers: null,
   bool withJavascript: true,
   bool clearCache: false,
   bool clearCookies: false,
   bool hidden: false,
   bool enableAppScheme: true,
   Rect rect: null,
   String userAgent: null,
   bool withZoom: false,
   bool withLocalStorage: true,
   bool withLocalUrl: true,
   bool scrollBar: true,
   bool supportMultipleWindows: false,
   bool appCacheEnabled: false,
   bool allowFileURLs: false,
});
```

```dart
Future<String> evalJavascript(String code);
```

```dart
Future<Map<String, dynamic>> getCookies();
```

```dart
Future<Null> cleanCookies();
```

```dart
Future<Null> resize(Rect rect);
```

```dart
Future<Null> show();
```

```dart
Future<Null> hide();
```

```dart
Future<Null> reloadUrl(String url);
```

```dart
Future<Null> close();
```

```dart
Future<Null> reload();
```

```dart
Future<bool> canGoBack();
```

```dart
Future<Null> goBack();
```

```dart
Future<bool> canGoForward();
```

```dart
Future<Null> goForward();
```

```dart
Future<Null> stopLoading();
```

