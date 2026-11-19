package cn.ppps.forwarder.server.component

import cn.ppps.forwarder.utils.Log
import com.google.gson.GsonBuilder
import cn.ppps.forwarder.server.model.BaseRequest
import cn.ppps.forwarder.utils.Base64
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.RSACrypt
import cn.ppps.forwarder.utils.SM4Crypt
import com.xuexiang.xrouter.utils.TextUtils
import com.xuexiang.xutil.data.ConvertTools
import com.yanzhenjie.andserver.annotation.Converter
import com.yanzhenjie.andserver.error.HttpException
import com.yanzhenjie.andserver.framework.MessageConverter
import com.yanzhenjie.andserver.framework.body.JsonBody
import com.yanzhenjie.andserver.framework.body.StringBody
import com.yanzhenjie.andserver.http.ResponseBody
import com.yanzhenjie.andserver.util.IOUtils
import com.yanzhenjie.andserver.util.MediaType
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type
import java.nio.charset.Charset

@Suppress("PrivatePropertyName")
@Converter
class AppMessageConverter : MessageConverter {

    private val TAG: String = "AppMessageConverter"

    override fun convert(output: Any?, mediaType: MediaType?): ResponseBody {
        //Return to Unified Structure Message
        var response = HttpServerUtils.response(output)

        return when (HttpServerUtils.safetyMeasures) {
            2 -> {
                val privateKey = RSACrypt.getPrivateKey(HttpServerUtils.serverPrivateKey)
                response = Base64.encode(response.toByteArray())
                response = RSACrypt.encryptByPrivateKey(response, privateKey)
                StringBody(response)
            }
            3 -> {
                val sm4Key = ConvertTools.hexStringToByteArray(HttpServerUtils.serverSm4Key)
                //response = Base64.encode(response.toByteArray())
                val encryptCBC = SM4Crypt.encrypt(response.toByteArray(), sm4Key)
                StringBody(ConvertTools.bytes2HexString(encryptCBC))
            }
            else -> JsonBody(response)
        }
    }

    @Throws(IOException::class)
    override fun <T : Any?> convert(stream: InputStream, mediaType: MediaType?, type: Type?): T? {
        val charset: Charset? = mediaType?.charset
        Log.d(TAG, "Charset: $charset")

        var json = if (charset == null) IOUtils.toString(stream) else IOUtils.toString(stream, charset)

        if (HttpServerUtils.safetyMeasures == 2) {
            if (TextUtils.isEmpty(HttpServerUtils.serverPrivateKey)) {
                Log.e(TAG, "RSADecrypt failed: Private key is empty")
                throw HttpException(500, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_server_private_key))
            }

            val privateKey = RSACrypt.getPrivateKey(HttpServerUtils.serverPrivateKey)
            json = RSACrypt.decryptByPrivateKey(json, privateKey)
            json = String(Base64.decode(json))
        } else if (HttpServerUtils.safetyMeasures == 3) {
            if (TextUtils.isEmpty(HttpServerUtils.serverSm4Key)) {
                Log.e(TAG, "SM4Decrypt failed: SM4Key is empty")
                throw HttpException(500, cn.ppps.forwarder.App.context.getString(cn.ppps.forwarder.R.string.copy_server_sm4_key))
            }

            val sm4Key = ConvertTools.hexStringToByteArray(HttpServerUtils.serverSm4Key)
            val encryptCBC = ConvertTools.hexStringToByteArray(json)
            val decryptCBC = SM4Crypt.decrypt(encryptCBC, sm4Key)
            //json = String(Base64.decode(decryptCBC.toString()))
            json = String(decryptCBC)
        }

        //Modifying interface datanull, ''is the default value
        val builder = GsonBuilder()
        builder.registerTypeAdapter(Int::class.java, IntegerDefaultAdapter())
        builder.registerTypeAdapter(String::class.java, StringDefaultAdapter())
        val gson = builder.create()
        val t: T? = gson.fromJson(json, type)
        Log.d(TAG, "Bean: $t")

        //Validation of time stamp (no time error beyond)1Hours）&& Signature
        if (HttpServerUtils.safetyMeasures == 1) {
            HttpServerUtils.checkSign(t as BaseRequest<*>)
        }

        return t
    }

}