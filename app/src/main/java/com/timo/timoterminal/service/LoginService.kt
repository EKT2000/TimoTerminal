package com.timo.timoterminal.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.provider.Settings.*
import android.util.Log
import com.timo.timoterminal.entityClasses.ConfigEntity
import com.timo.timoterminal.enums.SharedPreferenceKeys
import com.timo.timoterminal.repositories.ConfigRepository
import com.timo.timoterminal.utils.Utils
import com.zkteco.android.core.sdk.sources.IHardwareSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import java.util.UUID


class LoginService(
    private val httpService: HttpService,
    private val configRepository: ConfigRepository,
    private val sharedPrefService: SharedPrefService,
    private val hardware: IHardwareSource
) : KoinComponent {

    fun loginProcess(
        company: String,
        username: String,
        password: String,
        customUrl: String?,
        context: Context,
        coroutineScope: CoroutineScope,
        successCallback: () -> Unit?
    ) {
        if (Utils.isOnline(context)) {
            if (customUrl.isNullOrEmpty()) {
                getURlFromServer(
                    company,
                    coroutineScope,
                    context
                ) { url ->
                    loginCompany(
                        company,
                        username,
                        password,
                        url,
                        context,
                        coroutineScope,
                        successCallback,
                    )
                };
            } else {
                loginCompany(
                    company,
                    username,
                    password,
                    customUrl,
                    context,
                    coroutineScope,
                    successCallback
                )
            }
        } else {

            /*
            If we are offline, we need to check if
            - if a timo_id is existent
            - if a company is saved persistently and equal to inputs
            - if the password and username hash is identical to saved hashs
            (there has to be at least some kind of security)
            - if there is a token (indicator whether it has been validated online at least once)
             */


        }
    }

    private fun getURlFromServer(
        company: String,
        coroutineScope: CoroutineScope,
        context: Context,
        successCallback: (url: String) -> Unit?,
    ) {
        httpService.get(
            "https://www.timo24.de/timoadhttomin/baseurl",
            mapOf(Pair("company", company)),
            context,
            { _, _, url ->
                if (!url.isNullOrEmpty()) {
                    coroutineScope.launch {
                        successCallback(url)
                    }
                }
            }
        )
    }

    @SuppressLint("HardwareIds")
    private fun loginCompany(
        company: String,
        username: String,
        password: String,
        url: String,
        context: Context,
        coroutineScope: CoroutineScope, callback: () -> Unit?
    ) {
        coroutineScope.launch {

            //if not created, create a ID for the device. This ID is the recognizer for the timo
            // system and will be used as terminal id
            val hashedAndroidId = Utils.sha256(Secure.getString(context.contentResolver, Secure.ANDROID_ID))


            if (Utils.isOnline(context)) {
                val parameters = HashMap<String, String>()
                val timoTerminalId =
                    sharedPrefService.getInt(SharedPreferenceKeys.TIMO_TERMINAL_ID, -1)
                val ip = Utils.getIPAddress(true)

                parameters["id"] = timoTerminalId.toString()
                parameters["company"] = company
                parameters["password"] = password
                parameters["username"] = username
                parameters["androidId"] = String(hashedAndroidId)
                parameters["device"] = hardware.getDevice().naming
                parameters["brand"] = Build.BRAND
                parameters["ip"] = ip
                //Serial number is used as terminalId
                parameters["serialNumber"] = hardware.serialNumber()

                httpService.post(
                    "${url}services/rest/zktecoTerminal/loginTerminal", parameters,
                    context,
                    { res, _, _ ->
                        val payload = res?.getJSONObject("payload");
                        val terminalObj = payload?.getJSONObject("terminal")
                        val id = terminalObj?.getInt("id")
                        val token = terminalObj?.getString("token")
                        if (id != null && !token.isNullOrEmpty()) {
                            sharedPrefService.saveLoginCredentials(
                                String(hashedAndroidId),
                                id,
                                token,
                                url,
                                company,
                                username,
                                password
                            )
                            callback()
                        }
                    }
                )
            }


            //OFFLINE
            //check if permissions, company und users are stored persistently
            //if everything is loaded, directly forward to the mainactivity

        }
    }

    fun loadPermissions(
        coroutineScope: CoroutineScope,
        context :Context,
        callback: (worked: Boolean) -> Unit?
    ) {
        val url = sharedPrefService.getString(SharedPreferenceKeys.SERVER_URL)
        val company = sharedPrefService.getString(SharedPreferenceKeys.COMPANY)
        if (!url.isNullOrEmpty() && !company.isNullOrEmpty()) {
            httpService.get(
                "${url}services/rest/zktecoTerminal/permission",
                mapOf(Pair("firma", company)),
                context,
                { _, array, _ ->
                    if (array != null && array.length() > 0) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            addConfig(
                                coroutineScope,
                                ConfigEntity(
                                    ConfigRepository.TYPE_PERMISSION,
                                    obj.getString("name"),
                                    obj.getString("value")
                                )
                            )
                        }
                        //callback should only be called if the array is really loaded!
                        callback(true)
                    }
                }
            )
        }
        callback(false)
    }

    private fun addConfig(scope: CoroutineScope, config: ConfigEntity) {
        scope.launch {
            configRepository.insertConfigEntity(config)
        }
    }
}