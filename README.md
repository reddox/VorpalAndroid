# VorpalAndroid

## Init

* clone with `git clone --rceursive`
* inside app\src\main\assets\VorpalBlockly run `npm install`

# Known issues / limitations

* Major
    * Permission management is incomplete causing to crashes if Permissions requests are denied and requiring a reinstall of the App as the requests don't seem to reappear otherwise (also it requests nearby-share, which is not really required)
* Minor
    * "Beep-Stuttering": if the Beep is longer than 100ms. This is mainly a Hexapod issue (bluetooth communication interrupt the beeping) but could be avoided from Application side (as we don't really need to send updates every 100ms)
* "I'm not done yet"
    * a lot of missing features (only beeping and walking in W1 with the 4 main buttons is supported)
    * the option to set a Gamepad connection is there, but has no use (yet)
    * Sensor support is completely mssing