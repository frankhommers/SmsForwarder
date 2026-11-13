package cn.ppps.forwarder.entity.action

import java.io.Serializable

data class AlarmSetting(
    var description: String = "", //Description
    var action: String = "stop", //Actions: start=Activate alarm., stop=Stop the alarm.
    var volume: Int = 80, //Play Volume，0-100
    var playTimes: Int = 1, //Plays，0=Infinite Cycle，-1=Disable
    var music: String = "", //Music Files
    var repeatTimes: Int = 5, //Vibration repetitions，0=Infinite Cycle，-1=Disable
    var vibrate: String = "---___===___", //Vibration pattern：=Strong vibration, -Weak Vibration, _No vibrating, each lasting100ms
    var flashTimes: Int = 5, //Flash Number，0=Infinite Cycle，-1=Disable
    var flash: String = "XXOOXXOO", //Flash pattern：XLights on., OLights out., each lasting100ms
) : Serializable
