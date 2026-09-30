package org.astrofixxer.update

import org.json.JSONException
import org.json.JSONObject

class MalformedException(message: String) : Exception(message)

class ReleaseAsset(val name: String, val url: String, val size: Long)
class Release(val tag: String, val assets: List<ReleaseAsset>)

/** Reads GitHub's release JSON and AstroFixxer's update.json, strictly: a wrong type or shape is an error, never a guess. */
object UpdateParser {
    private val APP_ID = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val COMMIT = Regex("[0-9a-fA-F]{7,40}")

    private fun obj(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw MalformedException("not valid JSON")
    }

    private fun JSONObject.string(key: String): String? = if (has(key)) opt(key) as? String else null
    private fun JSONObject.integer(key: String): Long? = when (val v = if (has(key)) opt(key) else null) {
        is Int -> v.toLong()
        is Long -> v
        else -> null
    }

    /** The release: its tag and assets (name, download address, size). Assets without a name and address are skipped. */
    fun parseRelease(json: String): Release {
        val o = obj(json)
        val tag = o.string("tag_name")?.takeIf { it.isNotEmpty() } ?: throw MalformedException("release has no tag_name")
        val list = if (o.has("assets")) o.opt("assets") as? org.json.JSONArray ?: throw MalformedException("assets is not a list") else null
        val assets = ArrayList<ReleaseAsset>()
        if (list != null) for (i in 0 until list.length()) {
            val a = list.optJSONObject(i) ?: continue
            val name = a.string("name") ?: continue
            val url = a.string("browser_download_url") ?: continue
            assets += ReleaseAsset(name, url, a.integer("size") ?: -1L)
        }
        return Release(tag, assets)
    }

    /** update.json. [UpdateInfo.apkUrl] and [UpdateInfo.releaseTag] are filled in by the checker from the release. */
    fun parseUpdateJson(json: String): UpdateInfo {
        val o = obj(json)
        val code = o.integer("versionCode")?.takeIf { it in 1..Int.MAX_VALUE } ?: throw MalformedException("bad versionCode")
        val name = o.string("versionName")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 && it.none { c -> c < ' ' || c == '\u007f' } }
            ?: throw MalformedException("bad versionName")
        val appId = o.string("applicationId")?.takeIf { it.length <= 255 && APP_ID.matches(it) } ?: throw MalformedException("bad applicationId")
        val apk = o.string("apk")?.takeIf { UrlPolicy.isSafeSegment(it) && it.endsWith(".apk") && it.length > 4 } ?: throw MalformedException("bad apk name")
        val sha = o.string("sha256")?.takeIf { Integrity.isSha256Hex(it) }?.lowercase() ?: throw MalformedException("bad sha256")
        val size = o.integer("size")?.takeIf { it in 1..UpdateConfig.MAX_APK_BYTES } ?: throw MalformedException("bad size")
        val commit = o.string("commit")?.takeIf { COMMIT.matches(it) }?.lowercase()
        // A missing flag counts as "not permanent": the user then gets the cautious warning. A flag of the wrong type is an error.
        val permanent = if (o.has("permanentKey")) (o.opt("permanentKey") as? Boolean ?: throw MalformedException("bad permanentKey")) else false
        return UpdateInfo(code, name, appId, apk, sha, size, commit, permanent)
    }
}
