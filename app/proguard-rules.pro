# BYD's car classes are loaded and called reflectively at runtime from the head unit's own APK;
# they are never part of this app, so nothing of ours needs keeping for them.
# Keep line numbers so a crash log from the car is readable.
-keepattributes SourceFile,LineNumberTable
