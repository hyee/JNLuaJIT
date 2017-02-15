# Overview #

Max Raskin has done a port of JNLua for the [Android platform](https://github.com/airminer/jnlua-android).

Ignazio Di Napoli has collected the following notes for porting JNLua (and Lua) to the Android platform:

  * Modify Lua source code (locales are not supported in native SDK)
  * Remove javavm and .script namespace (not supported in Android)
  * Remove support for properties in `DefaultJavaReflector` (beans are not supported)
  * Remove `NavigableMap` support (not supported in Android < 2.3)