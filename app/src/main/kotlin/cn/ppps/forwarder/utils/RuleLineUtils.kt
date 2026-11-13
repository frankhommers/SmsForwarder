package cn.ppps.forwarder.utils

import cn.ppps.forwarder.R
import cn.ppps.forwarder.entity.MsgInfo
import com.xuexiang.xutil.resource.ResUtils.getString
import java.util.*

@Suppress("unused")
object RuleLineUtils {
    const val TAG = "RuleLineUtils"
    private var START_LOG = false

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val a = """and is mobile equals 10086
 or is mobile endsWith example
  and is sms contains test
 or is mobile endsWith pppscn
and is mobile equals 100861
and is mobile equals 100861"""
        val msg = MsgInfo("sms", "10086", "example", Date(), "15888888888")
        logg("check:" + checkRuleLines(msg, a))
    }

    fun startLog(startLog: Boolean) {
        START_LOG = startLog
    }

    private fun logg(msg: String?) {
        if (START_LOG) {
            Log.i(TAG, msg!!)
        }
    }

    @Throws(Exception::class)
    fun checkRuleLines(msg: MsgInfo, ruleLines: String?): Boolean {
        val scanner = Scanner(ruleLines)
        var lineNum = 0
        var headRuleLine: RuleLine? = null
        var beforeRuleLine: RuleLine? = null
        while (scanner.hasNextLine()) {
            val line = scanner.nextLine()
            logg("$lineNum : $line")
            //First line
            if (lineNum == 0) {
                //Indent is not allowed in the first row
                if (line.startsWith(" ")) {
                    throw Exception(getString(R.string.no_indentation_allowed_on_the_first_line))
                }
            }

            // process the line
            beforeRuleLine = generateRuleTree(line, lineNum, beforeRuleLine)
            if (lineNum == 0) {
                headRuleLine = beforeRuleLine
            }
            lineNum++
        }
        assert(headRuleLine != null)
        return checkRuleTree(msg, headRuleLine)
    }

    /**
     * Use a rule tree to judge if the message hits.
     * RuleWhether or not the node is hit depends on whether the node is hit, whether the node is hit, if any, and whether the node below the node, if any, is hit
     * Recursive Check
     */
    @Throws(Exception::class)
    fun checkRuleTree(msg: MsgInfo, currentRuleLine: RuleLine?): Boolean {
        //Whether or not the node hit
        var currentAll = currentRuleLine!!.checkMsg(msg)
        logg("current:$currentRuleLine checked:$currentAll")

        //Whether the node node (if any) was hit
        if (currentRuleLine.getChildRuleLine() != null) {
            logg(" child:" + currentRuleLine.getChildRuleLine())
            currentAll = when (currentRuleLine.getChildRuleLine()!!.conjunction) {
                RuleLine.CONJUNCTION_AND -> currentAll && checkRuleTree(msg, currentRuleLine.getChildRuleLine())
                RuleLine.CONJUNCTION_OR -> currentAll || checkRuleTree(msg, currentRuleLine.getChildRuleLine())
                else -> throw Exception("child wrong conjunction")
            }
        }

        //Whether the bottom of the node, if any, was hit
        if (currentRuleLine.getNextRuleLine() != null) {
            logg("next:" + currentRuleLine.getNextRuleLine())
            currentAll = when (currentRuleLine.getNextRuleLine()!!.conjunction) {
                RuleLine.CONJUNCTION_AND -> currentAll && checkRuleTree(msg, currentRuleLine.getNextRuleLine())
                RuleLine.CONJUNCTION_OR -> currentAll || checkRuleTree(msg, currentRuleLine.getNextRuleLine())
                else -> throw Exception("next wrong conjunction")
            }
        }
        return currentAll
    }

    /**
     * Generate a rule tree
     * One line represents a rule.
     */
    @Throws(Exception::class)
    fun generateRuleTree(line: String, lineNum: Int, parentRuleLine: RuleLine?): RuleLine {
        //val words = line.split(" ".toRegex()).toTypedArray()
        return RuleLine(line, lineNum, parentRuleLine)
    }
}