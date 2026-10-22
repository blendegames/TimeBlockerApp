# FocusTask release build rules.
# Room, DataStore, Glance and Compose bring their own rules. Nothing in the app uses reflection on its own classes.

# The app's enums are saved by name (settings, JSON copy): keep their names exactly as written.
-keep enum com.focustask.** { *; }
