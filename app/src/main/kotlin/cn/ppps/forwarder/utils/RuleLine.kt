package cn.ppps.forwarder.utils

import cn.ppps.forwarder.R
import cn.ppps.forwarder.entity.MsgInfo
import com.xuexiang.xutil.resource.ResUtils.getString
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@Suppress("unused")
class RuleLine(line: String, lineNum: Int, beforeRuleLine: RuleLine?) {
    companion object {
        val CONJUNCTION_AND: String = getString(R.string.CONJUNCTION_AND)
        val CONJUNCTION_OR: String = getString(R.string.CONJUNCTION_OR)
        val FILED_PHONE_NUM: String = getString(R.string.FILED_PHONE_NUM)
        val FILED_MSG_CONTENT: String = getString(R.string.FILED_MSG_CONTENT)
        val FILED_PACKAGE_NAME: String = getString(R.string.FILED_PACKAGE_NAME)
        val FILED_UID: String = getString(R.string.FILED_UID)
        val FILED_INFORM_TITLE: String = getString(R.string.FILED_INFORM_TITLE)
        val FILED_INFORM_CONTENT: String = getString(R.string.FILED_INFORM_CONTENT)
        val FILED_SIM_SLOT_INFO: String = getString(R.string.FILED_SIM_SLOT_INFO)
        val FILED_CALL_TYPE: String = getString(R.string.FILED_CALL_TYPE)
        val SURE_YES: String = getString(R.string.SURE_YES)
        val SURE_NOT: String = getString(R.string.SURE_NOT)
        val CHECK_EQUALS: String = getString(R.string.CHECK_EQUALS)
        val CHECK_CONTAIN: String = getString(R.string.CHECK_CONTAIN)
        val CHECK_NOT_CONTAIN: String = getString(R.string.CHECK_NOT_CONTAIN)
        val CHECK_START_WITH: String = getString(R.string.CHECK_START_WITH)
        val CHECK_END_WITH: String = getString(R.string.CHECK_END_WITH)
        val CHECK_REGEX: String = getString(R.string.CHECK_REGEX)
        val CONJUNCTION_LIST: MutableList<String> = ArrayList()
        val FILED_LIST: MutableList<String> = ArrayList()
        val SURE_LIST: MutableList<String> = ArrayList()
        val CHECK_LIST: MutableList<String> = ArrayList()

        const val TAG = "RuleLine"
        private var START_LOG = true
        fun startLog(startLog: Boolean) {
            START_LOG = startLog
        }

        fun logg(msg: String?) {
            if (START_LOG) {
                Log.i(TAG, msg!!)
            }
        }

        init {
            CONJUNCTION_LIST.add("and")
            CONJUNCTION_LIST.add("or")
            CONJUNCTION_LIST.add(CONJUNCTION_AND)
            CONJUNCTION_LIST.add(CONJUNCTION_OR)
        }

        init {
            FILED_LIST.add(FILED_PHONE_NUM)
            FILED_LIST.add(FILED_PACKAGE_NAME)
            FILED_LIST.add(FILED_MSG_CONTENT)
            FILED_LIST.add(FILED_INFORM_CONTENT)
            FILED_LIST.add(FILED_INFORM_TITLE)
            FILED_LIST.add(FILED_SIM_SLOT_INFO)
            FILED_LIST.add(FILED_CALL_TYPE)
            FILED_LIST.add(FILED_UID)
        }

        init {
            SURE_LIST.add(SURE_YES)
            SURE_LIST.add(SURE_NOT)
        }

        init {
            CHECK_LIST.add(CHECK_EQUALS)
            CHECK_LIST.add(CHECK_CONTAIN)
            CHECK_LIST.add(CHECK_NOT_CONTAIN)
            CHECK_LIST.add(CHECK_START_WITH)
            CHECK_LIST.add(CHECK_END_WITH)
            CHECK_LIST.add(CHECK_REGEX)
        }
    }

    //A few spaces at the beginning.
    private var headSpaceNum = 0
    private var beforeRuleLine: RuleLine? = null
    private var nextRuleLine: RuleLine? = null
    private var parentRuleLine: RuleLine? = null
    private var childRuleLine: RuleLine? = null

    //and or
    var conjunction: String

    //Phone number, SMS content APPPackage name, notification title, notification content, SIM slot
    private var field: String

    // Whether
    private var sure: String
    private var check: String
    private var value: String

    //Field Branch
    fun checkMsg(msg: MsgInfo): Boolean {

        //Checks if the result of the merger between this line and the previous line is hit
        var mixChecked = false
        when (field) {
            FILED_PHONE_NUM, FILED_PACKAGE_NAME -> mixChecked = checkValue(msg.from)
            FILED_UID -> mixChecked = checkValue(msg.uid.toString())
            FILED_CALL_TYPE -> mixChecked = checkValue(msg.callType.toString())
            FILED_MSG_CONTENT, FILED_INFORM_CONTENT -> mixChecked = checkValue(msg.content)
            FILED_INFORM_TITLE, FILED_SIM_SLOT_INFO -> mixChecked = checkValue(msg.simInfo)
            else -> {}
        }
        when (sure) {
            SURE_YES -> {}
            SURE_NOT -> mixChecked = !mixChecked
            else -> mixChecked = false
        }
        logg("rule line checked:$mixChecked")
        return mixChecked
    }

    //Content Branch
    private fun checkValue(msgValue: String?): Boolean {
        if (msgValue == null) return false

        fun evaluateCondition(condition: String): Boolean {
            return when (check) {
                CHECK_EQUALS -> msgValue == condition
                CHECK_CONTAIN -> msgValue.contains(condition)
                CHECK_NOT_CONTAIN -> !msgValue.contains(condition)
                CHECK_START_WITH -> msgValue.startsWith(condition)
                CHECK_END_WITH -> msgValue.endsWith(condition)
                CHECK_REGEX -> try {
                    val pattern = Pattern.compile(condition, Pattern.CASE_INSENSITIVE)
                    val matcher = pattern.matcher(msgValue)
                    matcher.find()
                } catch (e: PatternSyntaxException) {
                    logg("PatternSyntaxException: ${e.description}, Index: ${e.index}, Message: ${e.message}, Pattern: ${e.pattern}")
                    false
                }

                else -> false
            }
        }

        fun parseAndEvaluate(expression: String): Boolean {
            // Split by "||" and evaluate each segment joined by "&&"
            val orGroups = expression.split("||")
            return orGroups.any { orGroup ->
                val andGroups = orGroup.split("&&")
                andGroups.all { andGroup ->
                    val trimmedCondition = andGroup.trim()
                    evaluateCondition(trimmedCondition)
                }
            }
        }

        val checked = if (value.contains("&&") || value.contains("||")) {
            parseAndEvaluate(value)
        } else {
            evaluateCondition(value)
        }

        logg("checkValue $check checked:$checked")
        return checked
    }

    override fun toString(): String {
        return "RuleLine{" +
                "headSpaceNum='" + headSpaceNum + '\'' +
                "conjunction='" + conjunction + '\'' +
                ", field='" + field + '\'' +
                ", sure='" + sure + '\'' +
                ", check='" + check + '\'' +
                ", value='" + value + '\'' +
                '}'
    }

    fun getNextRuleLine(): RuleLine? {
        return nextRuleLine
    }

    fun setNextRuleLine(nextRuleLine: RuleLine?) {
        this.nextRuleLine = nextRuleLine
    }

    fun getChildRuleLine(): RuleLine? {
        return childRuleLine
    }

    fun setChildRuleLine(childRuleLine: RuleLine?) {
        this.childRuleLine = childRuleLine
    }

    init {
        logg("----------$lineNum-----------------")
        logg(line)
        //Rule-test：
        //and is mobile equals 10086
        //[and, Yes, mobile, equals, 10086]
        //  and is content contains test
        //[, , and, Yes, Contents, contains, sms]

        //Processing headspaces to confirm the relative position of a line node: same or lower
        //Process4Fields, everything after that.value

        //Tags3Phase
        var isCountHeading = false
        var isDealMiddle = false
        var isDealValue = false

        //For saving4intermediate tokens: and, Yes, Contents, contains
        val middleList: MutableList<String> = ArrayList(4)
        //Save every middle string
        var buildMiddleWord = StringBuilder()
        val valueBuilder = StringBuilder()
        for (i in line.indices) {
            val w = line[i].toString()
            logg("walk over:$w")

            //Control phase
            //Let's start with the head.
            if (i == 0) {
                if (" " == w) {
                    logg("start to isCountHeading:")
                    isCountHeading = true
                } else {
                    //Directly into the treatment intermediate phase
                    isDealMiddle = true
                    logg("start to isDealMiddle:")
                }
            }
            //Non-space encountered while counting indentation; change phase:From the empty phase of processing to the middle phase of processing
            if (isCountHeading && " " != w) {
                logg("isCountHeading to isDealMiddle:")
                isCountHeading = false
                isDealMiddle = true
            }

            //Processing the intermediate. The number of intermediates is enough. Phase change: from processing the intermediate to processing value
            if (isDealMiddle && middleList.size == 4) {
                logg("isDealMiddle done middleList:$middleList")
                logg("isDealMiddle to isDealValue:")
                isDealMiddle = false
                isDealValue = true
            }
            logg("isCountHeading:$isCountHeading")
            logg("isDealMiddle:$isDealMiddle")
            logg("isDealValue:$isDealValue")
            if (isCountHeading) {
                logg("headSpaceNum++:$headSpaceNum")
                headSpaceNum++
            }
            if (isDealMiddle) {
                //Caught Spaces
                if (" " == w) {
                    buildMiddleWord = if (buildMiddleWord.isEmpty()) {
                        throw Exception(cn.ppps.forwarder.App.context.getString(R.string.copy_rule_spaces, lineNum))
                    } else {
                        //Generated an intermediate.
                        middleList.add(buildMiddleWord.toString())
                        logg("get Middle++:$buildMiddleWord")
                        StringBuilder()
                    }
                } else {
                    //Put it.wAppend to intermediate token
                    buildMiddleWord.append(w)
                    logg("buildMiddleWord length:" + buildMiddleWord.length + "buildMiddleWord:" + buildMiddleWord)
                }
            }
            if (isDealValue) {
                //Append all remaining characters tovalue
                valueBuilder.append(w)
            }
        }
        logg("isDealValue done valueBuilder:$valueBuilder")
        if (middleList.size != 4) {
            throw Exception(cn.ppps.forwarder.App.context.getString(R.string.copy_rule_tokens, lineNum))
        }


        //Rule Alignment
        if (beforeRuleLine != null) {
            logg("beforeRuleLine :$beforeRuleLine")
            logg("thisRuleLine :$this")

            //Same level
            if (headSpaceNum == beforeRuleLine.headSpaceNum) {
                logg("Same level")
                this.beforeRuleLine = beforeRuleLine
                beforeRuleLine.nextRuleLine = this
            }
            //Child level
            if (headSpaceNum - 1 == beforeRuleLine.headSpaceNum) {
                logg("Child level")
                parentRuleLine = beforeRuleLine
                beforeRuleLine.childRuleLine = this
            }
            //Find Parent Level
            if (headSpaceNum < beforeRuleLine.headSpaceNum) {
                //Match to the most recent peer
                var fBeforeRuleLine = beforeRuleLine.beforeRuleLine
                if (fBeforeRuleLine == null) {
                    fBeforeRuleLine = beforeRuleLine.parentRuleLine
                }
                while (fBeforeRuleLine != null) {
                    logg("fBeforeRuleLine$fBeforeRuleLine")

                    //Find sibling level
                    if (headSpaceNum == fBeforeRuleLine.headSpaceNum) {
                        logg("Parent level")
                        this.beforeRuleLine = fBeforeRuleLine
                        fBeforeRuleLine.nextRuleLine = this
                        break
                    } else {
                        //Find Up
                        var pBeforeRuleLine = fBeforeRuleLine.beforeRuleLine
                        if (pBeforeRuleLine == null) {
                            pBeforeRuleLine = fBeforeRuleLine.parentRuleLine
                        }
                        fBeforeRuleLine = pBeforeRuleLine
                    }
                }
            }
        } else {
            logg("Root Level")
        }
        conjunction = middleList[0]
        sure = middleList[1]
        field = middleList[2]
        check = middleList[3]
        value = valueBuilder.toString()
        if (!CONJUNCTION_LIST.contains(conjunction)) {
            throw Exception(cn.ppps.forwarder.App.context.getString(R.string.copy_rule_token_invalid, lineNum, CONJUNCTION_LIST.toString(), conjunction))
        }
        if (!FILED_LIST.contains(field)) {
            throw Exception(cn.ppps.forwarder.App.context.getString(R.string.copy_rule_token_invalid, lineNum, FILED_LIST.toString(), field))
        }
        if (!SURE_LIST.contains(sure)) {
            throw Exception(cn.ppps.forwarder.App.context.getString(R.string.copy_rule_token_invalid, lineNum, SURE_LIST.toString(), sure))
        }
        if (!CHECK_LIST.contains(check)) {
            throw Exception(cn.ppps.forwarder.App.context.getString(R.string.copy_rule_token_invalid, lineNum, CHECK_LIST.toString(), check))
        }
        logg("----------$lineNum==$this")
    }
}