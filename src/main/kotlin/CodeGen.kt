import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.thread


class CodeGen : AnAction() {
    private val NOTIFICATION_GROUP = NotificationGroup("OrmCodeGen", NotificationDisplayType.BALLOON, true)
    private val log: Logger = Logger.getInstance("OrmCodeGen")

    // 项目图片
    private val CODEGEN = IconLoader.getIcon("/icons/ktrom.png")
    
    //    PluginManager.getLogger().error("test");
    override fun actionPerformed(e: AnActionEvent) {

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (virtualFile.extension != "json") return;
        val jsonFilePath = virtualFile.path

        val genFolderURL = CodeGen::class.java.classLoader.getResource("/gen/version.txt")
        if (genFolderURL == null) {
            Messages.showMessageDialog(
                "can not read version file in Resource",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val version = genFolderURL.readText()
        log.info("code gen plugin version:$version")
        val ostype = OsCheck.getOperatingSystemType()
        val execFile = if (ostype == OsCheck.OSType.MacOS) "AntOrmGen$version" else "AntOrmGen$version.exe"
        //找到当前plugin所在的地方
        val pluginPath = PluginManager.getPlugin(PluginId.getId("com.yuzd.codegen.ktorm"))?.path?.absolutePath
        if (pluginPath.isNullOrEmpty()) {
            Messages.showMessageDialog(
                "can not get plugin `orm code gen` path",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val exePath = Paths.get(pluginPath, execFile)
        if (!Files.exists(exePath)) {
            try {
                CodeGen::class.java.classLoader.getResourceAsStream("/gen/$execFile")
                    .use { stream ->
                        Files.copy(stream, exePath)
                    }
            } catch (e: IOException) {
                Messages.showMessageDialog(
                    "load codeGen agent in plugin `orm code gen` fail",
                    "Error",
                    Messages.getErrorIcon()
                );
                return;
            }
        }

        if (!Files.exists(exePath)) {
            Messages.showMessageDialog(
                "load codeGen agent in plugin `orm code gen` fail",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val project = e.dataContext.getData(PlatformDataKeys.PROJECT)
        thread {
            val pb = ProcessBuilder(exePath.toString(), jsonFilePath)
            pb.redirectErrorStream(true);
            var process: Process? = null
            var result = -1;
            var msg = "";
            try {
                process = pb.start()//执行命令
                result = process.waitFor() //等待codegen结果
            } catch (e: Exception) {
                //针对mac
                if (e.message?.toLowerCase()?.contains("access denied")!!) {
                    //赋予文件夹权限
                    val cmd = ProcessBuilder("/bin/chmod", "-R", pluginPath, "777")
                    val cmdr = cmd.start()
                    if (cmdr.waitFor() == 0) {
                        //成功
                        process = pb.start()
                        result = process.waitFor()
                    } else {
                        msg = e.message!!;
                        PluginManager.getLogger().error(msg)
                        result == -99;
                    }
                }else{
                    PluginManager.getLogger().error(e)
                }
            }
            ApplicationManager.getApplication().invokeLater {
                when {
                    result == -99 -> {
                        val notice = NOTIFICATION_GROUP.createNotification(
                            msg,
                            NotificationType.ERROR
                        )
                        notice.notify(project)
                    }
                    result != 0 -> {
                        msg = process?.inputStream?.readAll() ?: "orm codegen fail"
                        PluginManager.getLogger().error(msg)
                        val notice = NOTIFICATION_GROUP.createNotification(
                            msg,
                            NotificationType.ERROR
                        )
                        notice.notify(project)
                    }
                    else -> {
                        val notice = NOTIFICATION_GROUP.createNotification("orm codegen success", NotificationType.INFORMATION)
                        notice.notify(project)
                    }
                }
            }
        }.start()
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isVisible = virtualFile != null && "json" == virtualFile.extension
    }

    private fun InputStream.readAll(): String {
        val sc = Scanner(this)
        val sb = StringBuffer()
        while (sc.hasNext()) {
            sb.append(sc.nextLine())
        }
        return sb.toString()
    }

}