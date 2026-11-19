package cn.ppps.forwarder.utils

import cn.ppps.forwarder.entity.LocationInfo
import cn.ppps.forwarder.entity.condition.LocationConditionRules
import cn.ppps.forwarder.entity.condition.LocationConditionRules.Field
import cn.ppps.forwarder.entity.condition.LocationSetting
import org.junit.Assert.*
import org.junit.Test

class LocationConditionRulesTest {

    @Test fun equatorAndPrimeMeridianAreValid() {
        val r = LocationConditionRules.validate("0", "0", "100")
        assertEquals(emptyList<Field>(), r.invalid)
        assertEquals(LocationConditionRules.Coordinates(0.0, 0.0, 100.0), r.coordinates)
        assertNotNull(LocationConditionRules.validate("0.0", "-0.0", "0.5").coordinates)
    }

    @Test fun boundariesInclusive() {
        assertNotNull(LocationConditionRules.validate("-180", "-90", "1").coordinates)
        assertNotNull(LocationConditionRules.validate("180", "90", "1e3").coordinates)
    }

    @Test fun outOfRangeAndNonFiniteRejectedPerField() {
        assertEquals(listOf(Field.LONGITUDE), LocationConditionRules.validate("180.0001", "0", "1").invalid)
        assertEquals(listOf(Field.LATITUDE), LocationConditionRules.validate("0", "-90.5", "1").invalid)
        assertEquals(listOf(Field.RADIUS), LocationConditionRules.validate("0", "0", "0").invalid)
        assertEquals(listOf(Field.RADIUS), LocationConditionRules.validate("0", "0", "-5").invalid)
        assertEquals(listOf(Field.LONGITUDE, Field.LATITUDE, Field.RADIUS), LocationConditionRules.validate("NaN", "Infinity", "1e999").invalid)
        assertEquals(listOf(Field.LONGITUDE, Field.LATITUDE, Field.RADIUS), LocationConditionRules.validate("", " ", null).invalid)
        assertEquals(listOf(Field.LONGITUDE), LocationConditionRules.validate("4.8.9", "1", "1").invalid)
        assertEquals(listOf(Field.LATITUDE), LocationConditionRules.validate("1", "0x10", "1").invalid)
    }

    @Test fun decimalCommaAccepted() {
        assertEquals(4.8952, LocationConditionRules.parse("4,8952")!!, 0.0)
        assertNull(LocationConditionRules.parse("1,2,3"))
        assertNull(LocationConditionRules.parse("1.000,5"))
    }

    @Test fun cachedFixDetectionDoesNotTreatZeroAsMissing() {
        assertFalse("Cleared cache is not a fix", LocationConditionRules.hasCachedFix(LocationInfo()))
        assertFalse(LocationConditionRules.hasCachedFix(null))
        assertTrue("A real 0/0 fix is valid", LocationConditionRules.hasCachedFix(LocationInfo(0.0, 0.0, "", "2026-11-18 10:00:00", "gps")))
        assertTrue(LocationConditionRules.hasCachedFix(LocationInfo(0.0, 52.37, "", "", "network")))
        assertFalse(LocationConditionRules.hasCachedFix(LocationInfo(Double.NaN, 0.0, "", "t", "gps")))
    }

    @Test fun legacyAddressDetectionIsReadOnlyAndTypeScoped() {
        val json = """{"description":"old","type":"to","calcType":"address","longitude":0.0,"latitude":0.0,"distance":0.0,"address":"Main Street"}"""
        val copy = String(json.toCharArray())
        assertTrue(LocationConditionRules.isLegacyAddressCondition(TASK_CONDITION_TO_ADDRESS, json))
        assertTrue(LocationConditionRules.isLegacyAddressCondition(TASK_CONDITION_LEAVE_ADDRESS, json.replace("\"to\"", "\"leave\"")))
        assertFalse(LocationConditionRules.isLegacyAddressCondition(TASK_CONDITION_TO_ADDRESS, json.replace("address\",\"lon", "distance\",\"lon")))
        assertFalse("Other condition types are never flagged", LocationConditionRules.isLegacyAddressCondition(TASK_CONDITION_TO_ADDRESS - 1, json))
        assertFalse(LocationConditionRules.isLegacyAddressCondition(TASK_CONDITION_TO_ADDRESS, "not json"))
        assertFalse(LocationConditionRules.isLegacyAddressCondition(TASK_CONDITION_TO_ADDRESS, ""))
        assertEquals(copy, json)
    }

    @Test fun newConditionFieldsStartEmpty() {
        assertEquals("", LocationConditionRules.fieldText(0.0, blank = true))
        assertEquals("0.0", LocationConditionRules.fieldText(0.0, blank = false))
        assertFalse("Old address defaults are no distance setting", LocationConditionRules.isValidDistanceSetting(LocationSetting(calcType = "address")))
        assertTrue("0/0 with a radius is", LocationConditionRules.isValidDistanceSetting(LocationSetting(longitude = 0.0, latitude = 0.0, distance = 25.0)))
    }
}
