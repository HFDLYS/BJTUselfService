import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

group = "team.bjtuss.bjtuselfservice"
version = "0.1.0"

val windowsPackageDisplayName = System.getenv("WINDOWS_PACKAGE_NAME")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: "交大自由行 KMP"
val windowsPackageDescription = System.getenv("WINDOWS_PACKAGE_DESCRIPTION")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: "交大自由行 Kotlin Multiplatform Windows 应用"

kotlin {
    jvm("windows") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val windowsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.compose.desktop.windows.x64)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.djl.pytorch.engine)
                implementation(libs.djl.pytorch.native.win)
            }
        }
        val windowsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

compose.desktop {
    application {
        mainClass = "team.bjtuss.bjtuselfservice.windows.MainKt"
        // jpackage 只在完整 JDK 有，Android Studio/PyCharm JBR 没有；打包显式指向本机完整 JDK。
        javaHome = providers.environmentVariable("WINDOWS_PACKAGE_JAVA_HOME")
            .orElse("C:/Users/zjg/jdk21/jdk-21.0.8+9")
            .get()
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            modules("java.sql")
            // 中文显示名。英文系统上 jpackage 默认选代码页 1252 的 en .wxl，WiX light 会把中文
            // 打成 `?????` 并报 311；CI 用 -Duser.language=zh 让它选代码页 936 的中文 .wxl。
            // WINDOWS_PACKAGE_NAME/… 仍可手动传 ASCII 兜底。
            packageName = windowsPackageDisplayName
            // jpackage 版本必须是三段数字。应用内显示版本为 1.8.4-Beta-SheetFix-Widget-fix2；
            // 安装器使用数值版本 1.8.4，靠 main.wxs IncludeMaximum=yes 覆盖同一大版本。
            packageVersion = "1.8.4"
            description = windowsPackageDescription
            vendor = "BJTUselfService Contributors"
            windows {
                // 系统级标准位置：C:\Program Files\<packageName>。
                // 双击 MSI 由 msiexec 在启动时前台弹出 UAC（不要改 Burn EXE 清单，
                // 会毁掉 WiX 数据包）。EXE 仍可作备用，但推荐用 MSI。
                perUserInstall = false
                dirChooser = true
                menu = true
                shortcut = true
                menuGroup = windowsPackageDisplayName
                // 固定升级码，后续同范围安装才能覆盖升级。
                upgradeUuid = "8f3a1c2e-7b64-4d91-a5e0-2c9b6f4d8a17"
                iconFile.set(project.file("src/windowsMain/resources/BJTUselfServiceKMP.ico"))
            }
        }
    }
}

val realJavaHomePath = providers.environmentVariable("WINDOWS_PACKAGE_JAVA_HOME")
    .orElse("C:/Users/zjg/jdk21/jdk-21.0.8+9")
    .get()
    .trimEnd('/', '\\')
val jpackageOverridePath = file("packaging/jpackage").absolutePath
val shimCsPath = file("packaging/JpackageShim.cs").absolutePath
val cscPath = "C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
val shimHomePath = file("${layout.buildDirectory.get().asFile}/jpackage-shim").absolutePath
val jpackageResourcesPath = file("${layout.buildDirectory.get().asFile}/compose/tmp/resources").absolutePath

val compileJpackageShim by tasks.registering {
    inputs.file(shimCsPath)
    inputs.dir(jpackageOverridePath)
    outputs.dir(shimHomePath)
    val realJdk = realJavaHomePath
    val overrideDir = jpackageOverridePath
    val shimSource = shimCsPath
    val compiler = cscPath
    val outHome = shimHomePath
    doLast {
        val bin = File(outHome, "bin")
        bin.mkdirs()
        check(File(compiler).isFile) { "找不到 csc.exe：$compiler" }
        val compile = ProcessBuilder(
            compiler,
            "/nologo",
            "/target:exe",
            "/out:${File(bin, "jpackage.exe").absolutePath}",
            shimSource,
        ).inheritIO().start()
        check(compile.waitFor() == 0) { "编译 jpackage shim 失败" }
        File(bin, "shim.config").writeText(
            "real=$realJdk/bin/jpackage.exe\noverride=$overrideDir\n",
            Charsets.UTF_8,
        )
    }
}

afterEvaluate {
    listOf("packageExe", "packageMsi").forEach { taskName ->
        tasks.named<AbstractJPackageTask>(taskName) {
            dependsOn(compileJpackageShim)
            // compose.desktop 也会写 javaHome，必须在 afterEvaluate 再盖回去。
            javaHome.set(shimHomePath)
            val overrideWxs = File(jpackageOverridePath, "main.wxs")
            val resourcesDir = jpackageResourcesPath
            doFirst {
                val dest = File(resourcesDir)
                dest.mkdirs()
                overrideWxs.copyTo(File(dest, "main.wxs"), overwrite = true)
            }
        }
    }
}
