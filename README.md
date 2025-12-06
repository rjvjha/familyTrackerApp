# FamilyTrackerApp

A live location emitter app for family members using TLDB client. Sending live location updates of the user to safe & chosen telegram user. The
 app runs and performs location tracking even when killed and send a resuming location tracking notification when device get's restarted.

Requires a telegram account to send & receive location updates.

## Use Case

This application is designed to provide peace of mind to families by allowing them to know the whereabouts of their loved ones. It
 is built with privacy and security in mind, ensuring that location data is only shared with trusted contacts. The app is ideal for:

*   Parents who want to know if their children have arrived safely at school or home.
*   Family members who want to keep an eye on elderly relatives who may be prone to
 wandering.
*   Groups of friends or colleagues who need to coordinate their movements.

The app uses a secure Telegram to transmit location data, so you can be sure that only authorized individuals can see it.

## Permissions

The app requires the following permissions to function correctly:

*   `ACCESS_FINE_LOCATION`: To get the precise location of the device.
*   `ACCESS_COARSE_LOCATION`: To get the approximate location of the device.
*   `INTERNET`: To send and receive location data.
*   `ACCESS_BACKGROUND_LOCATION`: To track the location even when the app is not in the foreground.
*   `FOREGROUND_SERVICE`: To run the location tracking service as a foreground service, which is less likely to be killed by the system.
*   `FOREGROUND_SERVICE_LOCATION`: Required for Android 14+ to use location in a foreground service.

*   `POST_NOTIFICATIONS`: Required for Android 13+ to show notifications.
*   `RECEIVE_BOOT_COMPLETED`: To restart the location tracking service when the device is rebooted.

## Important Dependencies

The app uses the following important dependencies:

*   [Google Play
 Services for Location](https://developers.google.com/android/guides/location-overview): To get the device's location.
*   [TDLib (Telegram Database Library)](https://github.com/tdlib/td): To interact with the Telegram API.
*   [AndroidX Work
Manager](https://developer.android.com/topic/libraries/architecture/workmanager): To schedule background tasks.
*   [Jetpack Compose](https://developer.android.com/jetpack/compose): For building the user interface.
