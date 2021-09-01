package tech.eritquearcus.mirai.plugin.kwr

import com.baidu.aip.ocr.AipOcr
import com.google.gson.Gson
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.code.MiraiCode
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.source
import org.json.JSONArray
import org.json.JSONObject
import toolgood.words.StringSearch
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream


object Ocr {
    //����APPID/AK/SK
    internal var APP_ID = ""
    internal var API_KEY = ""
    internal var SECRET_KEY = ""
    // ��ʼ��һ��AipOcr
    private val client by lazy{ AipOcr(APP_ID, API_KEY, SECRET_KEY) }

    // ��ѡ�������������Ӳ���
//    client.setConnectionTimeoutInMillis(2000)
//    client.setSocketTimeoutInMillis(60000)
    @JvmStatic
    fun main(image:String): String {
        // �����ѡ�������ýӿ�
        val options = HashMap<String, String>()
        options["detect_direction"] = "true"
        options["probability"] = "true"

        // ����Ϊ����ͼƬ·��
        val res = this.client.basicGeneral(image, options)
        val obj = JSONObject(res.toString(2))
        var temp = ""
        val it: JSONArray = obj["words_result"] as JSONArray
        for (i in 0 until it.length()){
            temp +=  (it.get(i) as JSONObject).getString("words")
        }
        return temp
    }
}

//����ͼƬ
fun downloadImage(url: String, file: File): File {
    val openConnection = URL(url).openConnection()
    //��ֹĳЩ��վ��ת����֤����
    openConnection.addRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36")
    //���ͼƬ�ǲ���gzipѹ��
    val bytes = if (openConnection.contentEncoding == "gzip") {
        GZIPInputStream(openConnection.getInputStream()).readBytes()
    } else {
        openConnection.getInputStream().readBytes()
    }
    file.writeBytes(bytes)
    return file
}

//��֤�ǲ���qȺ�ܼ�
fun verifyQQbot(sender: Member):Boolean{
    return (sender.nameCard == "QȺ�ܼ�" || sender.id == 2854196310)
}

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "tech.eritquearcus.KWR",
        name="����",
        version = "1.2.0"
    )
) {
    private var seachers: ArrayList<StringSearch> = ArrayList()
    //ͼƬ�������
    private var imgCache: Map<String, String> = mapOf()
    override fun onEnable() {
        logger.info("Keywords recall plugin loaded!")
        val f = File(dataFolder.absolutePath + "/config.json").let {
            if(!it.isFile || !it.exists())
                return
            else
                it
        }
        val config = Gson().fromJson(f.readText(), Config::class.java)
        if(config.readPic == null)
            config.readPic = false
        if(config.readText == null)
            config.readText = false
        if(config.notification == null)
            config.notification = false
        if(config.readPic!!)
            if(config.baiduSetting == null){
                logger.error("�ٶ�ocrδ����, ��ȡͼƬ���عر�")
                config.readPic = false
            }else{
                Ocr.API_KEY = config.baiduSetting.API_KEY
                Ocr.APP_ID = config.baiduSetting.APP_ID
                Ocr.SECRET_KEY = config.baiduSetting.SECRET_KEY
            }
        logger.info("�����ļ�·��${dataFolder.absolutePath}/config.txt")
        logger.info("����ʶ�𿪹�${config.readText}")
        logger.info("ͼƬʶ�𿪹�${config.readPic}")
        logger.info("���ر߽�ֵ${config.MaxBorder}")
        logger.info("Ŀǰ�ؼ�����:${config.keyWords}")
        for(a in config.keyWords){
            val tmp = StringSearch()
            tmp.SetKeywords(a)
            seachers.add(tmp)
        }
        if(!File(dataFolder.absolutePath + "/Imgcache/").exists())
            File(dataFolder.absolutePath + "/Imgcache/").mkdir()
        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            //QQ�ܼ�
            if (verifyQQbot(sender)) return@subscribeAlways

            var plainAll = ""
            var picAll = ""
            //�б�Ⱥ��ַ��������ִ��
            message.forEach {
                if (it is PlainText && config.readText!!) plainAll += it.contentToString()
                if (it is Image && config.readPic!!) {
                    //ͼƬ����
                    val id = it.imageId.split(".")[0]
                    val value = imgCache[id]
                    if (value != null) {
                        picAll += value
                        logger.info(picAll)
                    } else {
                        val url = it.queryUrl()
                        logger.info("ȡ��ͼƬ${url}")
                        //��BinaryTest.Excute()����ֵ��
                        val temp: File = downloadImage(url, File(dataFolder.absolutePath + "/Imgcache/$id.jpg"))
                        val tempa = Ocr.main(dataFolder.absolutePath + "/Imgcache/$id.jpg")
                            .replace("\n", "")//ȡ������
                            .replace(" ", "")//ȡ���ո�
                        logger.info("���$tempa")
                        imgCache = imgCache.plus(mapOf(id to tempa))
                        picAll += tempa
                        //�Զ�ɾ��ͼƬ����
                        temp.delete()
                    }
                }
            }
            var allp = 0
            if (config.readText!! || config.readPic!!) {
                var i = 0
                for (sc in seachers) {
                    i += 1
                    allp += sc.FindAll(plainAll).size * i + sc.FindAll(picAll).size * i
                    if (allp > config.MaxBorder) {
                        try {
                            message.source.recall()
                        } catch (e: PermissionDeniedException) {
                            logger.warning("����ʧ��:��������Ȩ��")
                        } catch (e: IllegalStateException) {
                            logger.warning("����ʧ��:��Ϣ�ѳ��ػ�Է�Ȩ�ޱ�bot����")
                        }
                        if(config.notification!!)
                            this.group.owner.sendMessage(MiraiCode.deserializeMiraiCode("[QQȺ${this.group.id}]����Υ����Ϣ[${this.message.serializeToMiraiCode()}]����Ⱥ��Ա[${this.sender.id}]"))
                        return@subscribeAlways
                    }
                }
            }
        }
    }
}
