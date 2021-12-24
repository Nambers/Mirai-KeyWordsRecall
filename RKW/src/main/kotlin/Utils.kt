/*
 * Copyright (c) 2020 - 2021. Eritque arcus and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version(in your opinion).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package tech.eritquearcus.mirai.plugin.rkw

import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import tech.eritquearcus.mirai.plugin.rkw.PluginMain.config
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream

//下载图片
fun downloadImage(url: String, file: File): File {
    val openConnection = URL(url).openConnection()
    //防止某些网站跳转到验证界面
    openConnection.addRequestProperty(
        "user-agent",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36"
    )
    //如果图片是采用gzip压缩
    val bytes = if (openConnection.contentEncoding == "gzip") {
        GZIPInputStream(openConnection.getInputStream()).readBytes()
    } else {
        openConnection.getInputStream().readBytes()
    }
    file.writeBytes(bytes)
    return file
}

suspend fun MessageChain.toText(): String {
    var plainAll = ""
    var picAll = ""
    this.forEach {
        if (it is PlainText && config.readText!!) plainAll += it.contentToString()
        if (it is Image && config.readPic!!) {
            //图片处理
            val id = it.imageId.split(".")[0]
            val value = PluginMain.imgCache[id]
            if (value != null) {
                picAll += value
                PluginMain.logger.info(picAll)
            } else {
                val url = it.queryUrl()
                PluginMain.logger.info("取到图片${url}")
                //用BinaryTest.Excute()来二值化
                val temp: File = downloadImage(url, File(PluginMain.dataFolder.absolutePath + "/Imgcache/$id.jpg"))
                val tempa = Ocr.main(PluginMain.dataFolder.absolutePath + "/Imgcache/$id.jpg")
                    .replace("\n", "")//取消换行
                    .replace(" ", "")//取消空格
                PluginMain.logger.info("结果$tempa")
                PluginMain.imgCache = PluginMain.imgCache.plus(mapOf(id to tempa))
                picAll += tempa
                //自动删除图片缓存
                temp.delete()
            }
        }
    }
    return plainAll + picAll
}

fun String.excessBorder(): Boolean {
    var i = 0
    var allp = 0
    for (sc in PluginMain.seachers) {
        i += 1
        allp += sc.FindAll(this).size * i
        if (allp > config.MaxBorder) {
            return true
        }
    }
    return false
}