package cn.ppps.forwarder.utils

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class WebhookEditorModelTest {
    @Test fun inferenceMatchesTransport() {
        assertEquals(WebhookEditorModel.Mode.JSON, WebhookEditorModel.infer("POST", " {\"x\":\"[msg]\"}", emptyMap()))
        assertEquals(WebhookEditorModel.Mode.JSON, WebhookEditorModel.infer("PATCH", "[]", mapOf("content-type" to "application/json")))
        assertEquals(WebhookEditorModel.Mode.LEGACY, WebhookEditorModel.infer("GET", "{\"x\":1}", emptyMap()))
        assertEquals(WebhookEditorModel.Mode.LEGACY, WebhookEditorModel.infer("GET", "/[from]", emptyMap()))
        assertEquals(WebhookEditorModel.Mode.LEGACY, WebhookEditorModel.infer("PUT", "hello", mapOf("Content-Type" to "text/plain")))
        assertEquals(WebhookEditorModel.Mode.PARAMETERS, WebhookEditorModel.infer("GET", "x=[msg]", mapOf("Content-Type" to "application/json")))
    }
    @Test fun unknownFieldsAndExactPayloadSurviveMerge() {
        val original = "{\"future\":{\"token\":\"unchanged\"},\"webParams\":\" a=%26&b=x=y&b=two& \"}"
        val edited = JsonParser.parseString("{\"method\":\"POST\"}").asJsonObject
        val result = JsonParser.parseString(WebhookEditorModel.merge(original, edited)).asJsonObject
        assertEquals("unchanged", result["future"].asJsonObject["token"].asString)
        assertEquals(" a=%26&b=x=y&b=two& ", result["webParams"].asString)
        assertEquals("POST", result["method"].asString)
    }
    @Test fun rowRoundTripsDoNotDecodeNormalizeOrReorder() {
        listOf("a=%26&b=x=y&b=two&" to "&", "+31612345678;{{FROM}}" to ";", "com.example.one\ncom.example.two\n" to "\n", "foo.*===bar\n(.*)===$1" to "\n", "^a.*$\n[b]" to "\n", "" to "&").forEach { (raw, delimiter) ->
            assertEquals(raw, ItemListCodec.join(ItemListCodec.split(raw, delimiter), delimiter))
        }
        assertEquals("token" to "x=y", ItemListCodec.parameter("token=x=y"))
        assertEquals("flag" to "", ItemListCodec.parameter("flag"))
    }
    @Test fun defaultGetBuildsQueryWithoutMutatingConfiguration() {
        val url = "https://example.org/hook"
        assertEquals("https://example.org/hook?from=%2B31&content=a+b", WebhookEditorModel.defaultGetUrl(url, "+31", "a b", 10, ""))
        assertEquals("https://example.org/hook?token=x&from=a&content=b&timestamp=10&sign=x%3D", WebhookEditorModel.defaultGetUrl("$url?token=x", "a", "b", 10, "x%3D"))
        assertEquals("https://example.org/hook", url)
    }
    @Test fun jsonValidation() {
        assertTrue(WebhookEditorModel.validJson("{\"text\":\"[msg]\"}"))
        assertTrue(WebhookEditorModel.validJson("[]"))
        assertFalse(WebhookEditorModel.validJson(""))
        assertFalse(WebhookEditorModel.validJson("{broken"))
        assertFalse(WebhookEditorModel.validJson("42"))
    }
    @Test fun catalogPreservesPersistedIds() {
        assertEquals(listOf(3, 1, 7, 11, 8, 2, 15, 14), TargetCatalog.availableTypes)
        listOf(0,4,5,6,9,10,12,13,99).forEach { assertFalse(TargetCatalog.isAvailable(it)) }
    }
}
